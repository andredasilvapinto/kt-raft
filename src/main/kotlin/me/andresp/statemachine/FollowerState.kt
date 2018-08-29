package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.CANDIDATE
import me.andresp.statemachine.StateId.FOLLOWER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FollowerState(private val node: Node, client: NodeClient) : AState(FOLLOWER, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FollowerState::class.java)
    }

    override suspend fun <T : Event> handle(e: T): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, node.cluster)
            is LeaderHeartbeatTimeout -> CANDIDATE
            is VoteRequested -> handleVoteRequested(e)
            is NodeJoined -> handleNodeJoined(e, node.cluster)
            else -> {
                logger.info("FollowerState doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }

    private suspend fun handleVoteRequested(e: VoteRequested): StateId {
        client.voteFor(node.nodeAddress, e.candidateAddress, e.electionTerm)
        return FOLLOWER
    }
}
