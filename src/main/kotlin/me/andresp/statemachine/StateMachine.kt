package me.andresp.statemachine

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.andresp.api.NodeJoinedPayload
import me.andresp.cluster.Node
import me.andresp.cluster.NodeAddress
import me.andresp.data.CommandProcessor
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule


class StateMachine(val node: Node, private val nodeClient: NodeClient, private val states: Map<StateId, AState>, initialState: AState) {
    @Volatile
    var currentState = initialState
        private set

    @Volatile
    private var timerTask: TimerTask? = null

    private val timer = Timer(true)

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StateMachine::class.java)
        val ELECTION_TIMEOUT_RANGE_MS = 15000..30000 // 150..300

        fun construct(node: Node, nodeClient: NodeClient, cmdProcessor: CommandProcessor): StateMachine {
            val states = mapOf(
                    DISCONNECTED to DisconnectedState(node, nodeClient, cmdProcessor),
                    FOLLOWER to FollowerState(node, nodeClient, cmdProcessor),
                    CANDIDATE to CandidateState(node, nodeClient, cmdProcessor),
                    LEADER to LeaderState(node, nodeClient, cmdProcessor)
            )
            return StateMachine(node, nodeClient, states, states[DISCONNECTED]!!)
        }
    }

    fun start(target: NodeAddress?) {
        if (target == null) {
            logger.info("Starting a new cluster")
            node.cluster.addNode(node.nodeAddress)
            transitionState(LEADER)
        } else {
            runBlocking {
                logger.info("Joining cluster via $target")
                try {
                    val clusterStatus = nodeClient.sendJoinNotification(target, NodeJoinedPayload(node.nodeAddress))
                    node.cluster.setStatus(clusterStatus)
                    transitionState(FOLLOWER)
                } catch (e: Exception) {
                    logger.error("Error when trying to join the cluster", e)
                    throw e
                }
            }
        }
    }

    fun scheduleElectionTimeout() {
        timerTask?.cancel()
        val delayMs = ELECTION_TIMEOUT_RANGE_MS.random().toLong()
        timerTask = timer.schedule(delayMs) {
            logger.info("Election timeout $delayMs ms reached. Injecting timeout event.")
            GlobalScope.launch {
                handle(ElectionTimeout(node.currentElectionTerm.number))
            }
        }
    }

    fun cancelElectionTimeout() {
        logger.info("Cancelling election timeout")
        timerTask?.cancel()
    }

    // Only one event at a time, only one current state at a time
    @Synchronized
    fun handle(ev: Event) {
        try {
            logger.info("Handling event $ev")
            val newStateId = currentState.handle(ev, this)
            transitionState(newStateId)
        } catch (e: Exception) {
            logger.error("Error handling $ev", e)
        }
    }

    private fun transitionState(newStateId: StateId) {
        val newState = states[newStateId]!!
        if (newState != currentState) {
            logger.info("STATE CHANGE: ${currentState.stateId} -> ${newState.stateId}")
            currentState.leave(this)
            currentState = newState
            currentState.enter(this)
        }
    }
}

fun ClosedRange<Int>.random() = Random().nextInt(endInclusive + 1 - start) + start
