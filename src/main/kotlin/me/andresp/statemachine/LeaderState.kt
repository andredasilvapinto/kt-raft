package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LeaderState(node: Node, client: NodeClient) : AState(LEADER, node, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LeaderState::class.java)
    }

    override fun enter(stateMachine: StateMachine) {
        // TODO Schedule Leader Heartbeats
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e)
            is NodeJoined -> handleNodeJoined(e)
            is VoteRequested -> handleVoteRequested(e)
            else -> {
                logger.info("LeaderState doesn't handle ${e.javaClass}. Ignoring.")
                LEADER
            }
        }
    }
}
