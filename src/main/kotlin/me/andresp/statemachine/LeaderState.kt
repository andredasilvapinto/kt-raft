package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule

class LeaderState(node: Node, client: NodeClient) : AState(LEADER, node, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LeaderState::class.java)
        const val LEADER_HEARTBEAT_PERIOD_MS = 500L
    }

    private val timer = Timer(true)
    private var timerTask: TimerTask? = null

    override fun enter(stateMachine: StateMachine) {
        node.handleNewLeader(node.nodeAddress, node.currentElectionTerm.number)
        stateMachine.cancelLeaderTimeout()
        scheduleHeartbeat()
    }

    private fun scheduleHeartbeat() {
        timerTask?.cancel()
        timerTask = timer.schedule(0, LEADER_HEARTBEAT_PERIOD_MS) {
            client.broadcast(node.cluster) {
                StateMachine.logger.info("Sending Leader Heartbeat...")
                val leaderHearbeat = LeaderHeartbeat(
                        node.currentElectionTerm.number,
                        node.nodeAddress,
                        node.cluster.getStatus()
                )
                client.sendHeartbeat(it, leaderHearbeat)
            }
        }
    }

    private fun cancelHeartbeat() = timerTask?.cancel()

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        val newStateId = when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, stateMachine)
            is NodeJoined -> handleNodeJoined(e)
            is VoteRequested -> handleVoteRequested(e)
            else -> {
                logger.info("LeaderState doesn't handle ${e.javaClass}. Ignoring.")
                LEADER
            }
        }

        if (newStateId != LEADER) {
            cancelHeartbeat()
        }

        return newStateId
    }
}
