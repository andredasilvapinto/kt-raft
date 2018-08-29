package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.CANDIDATE
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CandidateState(private val node: Node, client: NodeClient) : AState(CANDIDATE, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CandidateState::class.java)
    }

    private var receivedVotes = 0

    override fun enter() {
        logger.info("Should ask for votes")
    }

    override suspend fun <T : Event> handle(e: T): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, node.cluster)
            is VoteReceived -> handleVoteReceived(e)
            is NodeJoined -> handleNodeJoined(e, node.cluster)
            else -> {
                logger.info("Candidate doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }

    private fun handleVoteReceived(e: VoteReceived): StateId {
        return if (receivedVotes++ > node.cluster.totalNumberOfNodes / 2) {
            // TODO: Consider moving to Leader enter actions?
            node.cluster.updateLeader(node.nodeAddress, node.cluster.currentElectionTerm)
            LEADER
        } else {
            CANDIDATE
        }
    }
}
