package me.andresp.statemachine

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import me.andresp.cluster.Cluster
import me.andresp.cluster.Node
import me.andresp.cluster.NodeAddress
import me.andresp.config.config
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule


class StateMachine(val node: Node, private val nodeClient: NodeClient, private val states: Map<StateId, AState>, initialState: AState) {
    var currentState = initialState
        private set

    private val timer = Timer(true)
    private var timerTask: TimerTask? = null

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StateMachine::class.java)
        val ELECTION_TIMEOUT_RANGE_MS = 1500..3000 //150..300
        const val INITIAL_TIMEOUT_DELAY_MS = 5000L

        fun construct(cfg: Configuration, nodeClient: NodeClient, selfAddress: NodeAddress): StateMachine {
            val cluster = Cluster(cfg[config.numberNodes])
            val node = Node(selfAddress, cluster)
            val states = mapOf(
                    FOLLOWER to FollowerState(node, nodeClient),
                    CANDIDATE to CandidateState(node, nodeClient),
                    LEADER to LeaderState(node, nodeClient)
            )
            return StateMachine(node, nodeClient, states, states[FOLLOWER]!!)
        }
    }

    fun start(target: NodeAddress?) {
        if (target == null) {
            logger.info("Starting a new cluster")
            node.cluster.addNode(node.nodeAddress)
        } else {
            runBlocking {
                logger.info("Joining cluster via $target")
                val clusterStatus = nodeClient.join(target, node.nodeAddress)
                node.cluster.setStatus(clusterStatus)

            }
        }
        scheduleLeaderTimeout(INITIAL_TIMEOUT_DELAY_MS)
    }

    fun scheduleLeaderTimeout(extraDelayMs: Long = 0L) {
        timerTask?.cancel()
        val delayMs = extraDelayMs + ELECTION_TIMEOUT_RANGE_MS.random().toLong()
        timerTask = timer.schedule(delayMs) {
            logger.info("Leader timeout $delayMs ms reached. Injecting timeout event.")
            launch {
                handle(LeaderHeartbeatTimeout(node.currentElectionTerm.number))
            }
        }
    }

    fun cancelLeaderTimeout() {
        logger.info("Cancelling leader timeout")
        timerTask?.cancel()
    }

    // Only one event at a time, only one current state at a time
    @Synchronized
    fun handle(ev: Event) {
        try {
            val newStateId = currentState.handle(ev, this)
            transitionState(newStateId)
        } catch (e: Exception) {
            logger.error("Error handling $ev", e)
        }
    }

    private fun transitionState(newStateId: StateId) {
        val newState = states[newStateId]!!
        if (newState != currentState) {
            logger.info("STATE CHANGE: ${currentState.id} -> ${newState.id}")
            currentState = newState
            currentState.enter(this)
        }
    }
}

fun ClosedRange<Int>.random() = Random().nextInt(endInclusive + 1 - start) + start
