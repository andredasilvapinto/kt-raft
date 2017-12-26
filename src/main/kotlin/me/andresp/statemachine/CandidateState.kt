package me.andresp.statemachine

import me.andresp.statemachine.StateId.CANDIDATE
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CandidateState(private val node: Node) : State(CANDIDATE) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CandidateState::class.java)
    }

    private var receivedVotes = 0

    override fun enter() {
        logger.info("Should ask for votes")
    }

    override fun <T : Event> handle(e: T): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, node)
            is VoteReceived -> handleVoteReceived(e)
            else -> {
                logger.info("Candidate doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }

    private fun handleVoteReceived(e: VoteReceived): StateId {
        return if (receivedVotes++ > node.totalNumberOfNodes / 2) {
            // TODO: Consider moving to Leader enter actions?
            node.updateLeader(node.nodeId, node.currentElectionTerm)
            LEADER
        } else {
            CANDIDATE
        }
    }
}
