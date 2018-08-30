package me.andresp.statemachine

import kotlinx.coroutines.experimental.launch
import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.CANDIDATE
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CandidateState(node: Node, client: NodeClient) : AState(CANDIDATE, node, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CandidateState::class.java)
    }

    private var receivedVotes = 0

    override fun enter(stateMachine: StateMachine) {
        askForVotes(stateMachine)
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e)
            is VoteReceived -> handleVoteReceived(e)
            is NodeJoined -> handleNodeJoined(e)
            is VoteRequested -> handleVoteRequested(e)
            else -> {
                logger.info("Candidate doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }

    private fun askForVotes(stateMachine: StateMachine) {
        // TODO Confirm if this makes sense here
        node.newTerm()

        // Vote for itself
        stateMachine.handle(VoteReceived(node.currentElectionTerm.number, node.nodeAddress))

        node.cluster.nodeAddresses.minus(node.nodeAddress).map {
            launch {
                logger.info("Asking $it for a vote")
                val reply = client.askForVote(it, node.nodeAddress, node.currentElectionTerm.number)
                if (reply.voteGranted) {
                    stateMachine.handle(VoteReceived(node.currentElectionTerm.number, reply.voterAddress))
                }
            }
        }
    }

    private fun handleVoteReceived(e: VoteReceived): StateId {
        return if (++receivedVotes > node.cluster.nodeCount / 2) {
            // TODO: Consider moving to Leader enter actions?
            node.cluster.updateLeader(node.nodeAddress, e.electionTerm, node.currentElectionTerm.number)
            LEADER
        } else {
            CANDIDATE
        }
    }
}
