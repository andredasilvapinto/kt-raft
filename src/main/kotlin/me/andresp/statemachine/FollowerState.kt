package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.CANDIDATE
import me.andresp.statemachine.StateId.FOLLOWER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FollowerState(node: Node, client: NodeClient) : AState(FOLLOWER, node, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FollowerState::class.java)
    }

    override fun enter(stateMachine: StateMachine) {
        stateMachine.scheduleElectionTimeout()
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, stateMachine)
            is ElectionTimeout -> CANDIDATE
            is VoteRequested -> handleVoteRequested(e)
            is NodeJoinedRequest -> handleNodeJoined(e)
            else -> {
                logger.info("FollowerState doesn't handle ${e.javaClass}. Ignoring.")
                FOLLOWER
            }
        }
    }
}
