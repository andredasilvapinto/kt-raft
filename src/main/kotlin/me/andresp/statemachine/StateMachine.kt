package me.andresp.statemachine

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import me.andresp.cluster.Cluster
import me.andresp.cluster.Node
import me.andresp.cluster.NodeAddress
import me.andresp.config.config
import me.andresp.http.LOCAL_IP
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
        val ELECTION_TIMEOUT_RANGE_MS = 150..300

        fun construct(cfg: Configuration, nodeClient: NodeClient): StateMachine {
            val cluster = Cluster(cfg[config.numberNodes])
            val node = Node(NodeAddress(LOCAL_IP, cfg[config.httpPort]), cluster)
            val states = mapOf(
                    FOLLOWER to FollowerState(node, nodeClient),
                    CANDIDATE to CandidateState(node, nodeClient),
                    LEADER to LeaderState(node, nodeClient)
            )
            return StateMachine(node, nodeClient, states, states[FOLLOWER]!!)
        }
    }

    fun start(target: NodeAddress?) {
        if (target != null) {
            runBlocking {
                logger.info("Joining cluster via $target")
                nodeClient.join(target, node.nodeAddress)
            }
        } else {
            node.cluster.addNode(node.nodeAddress)
        }
        scheduleTimeout()
    }

    private fun scheduleTimeout() {
        timerTask?.cancel()
        // TODO: Kotlin Compiler Bug
        timerTask = timer.schedule(ELECTION_TIMEOUT_RANGE_MS.random().toLong(), {
            logger.info("Leader timeout reached. Injecting timeout event.")
            launch {
                handle(LeaderHeartbeatTimeout(node.cluster.currentElectionTerm))
            }
        })
    }

    suspend fun handle(ev: Event) {
        try {
            val newStateId = currentState.handle(ev)
            val newState = states[newStateId]!!
            if (newState != currentState) {
                logger.info("STATE CHANGE: ${currentState.id} -> ${newState.id}")
                currentState = newState
                currentState.enter()
            }
        } catch (e: Exception) {
            logger.error("Error handling $ev", e)
        }
    }
}

fun ClosedRange<Int>.random() = Random().nextInt(endInclusive + 1 - start) + start
