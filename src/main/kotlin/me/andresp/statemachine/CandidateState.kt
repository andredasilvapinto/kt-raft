package me.andresp.statemachine

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import me.andresp.cluster.Node
import me.andresp.cluster.NodeAddress
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CandidateState(node: Node, client: NodeClient) : AState(CANDIDATE, node, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CandidateState::class.java)
        const val VOTE_REQUEST_TIMEOUT_MS = 500
    }

    private val receivedVotes = mutableSetOf<NodeAddress>()

    @Volatile
    private var candidacyTerm: Int = 0

    override fun enter(stateMachine: StateMachine) {
        startElection(stateMachine)
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        return when (e) {
            is ElectionTimeout -> startElection(stateMachine)
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, stateMachine)
            is VoteReceived -> handleVoteReceived(e)
            is NodeJoinedRequest -> handleNodeJoined(e)
            is VoteRequested -> handleVoteRequested(e)
            else -> {
                logger.info("Candidate doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }

    private fun startElection(stateMachine: StateMachine): StateId {
        node.newTerm()
        candidacyTerm = node.currentElectionTerm.number
        askForVotes(stateMachine)
        stateMachine.scheduleElectionTimeout()
        return CANDIDATE
    }

    private fun askForVotes(stateMachine: StateMachine) {
        receivedVotes.clear()

        launch {
            // Vote for itself
            // needs to be done in a different coroutine so the synchronized handle
            // blocks this from running before the current event is completely processed
            stateMachine.handle(VoteReceived(node.currentElectionTerm.number, node.nodeAddress))
        }

        client.broadcast(node.cluster) {
            while (candidacyTerm == node.currentElectionTerm.number) {
                try {
                    logger.info("Asking $it for a vote")
                    val reply = client.askForVote(it, node.nodeAddress, node.currentElectionTerm.number)
                    if (reply.voteGranted) {
                        stateMachine.handle(VoteReceived(node.currentElectionTerm.number, reply.voterAddress))
                    }
                    break
                } catch (e: Exception) {
                    logger.error("Error asking for votes from $it", e)
                    delay(VOTE_REQUEST_TIMEOUT_MS)
                }
            }
        }
    }

    private fun handleVoteReceived(e: VoteReceived): StateId {
        if (e.electionTerm == node.currentElectionTerm.number) {
            receivedVotes.add(e.voterAddress)
            if (receivedVotes.count() > node.cluster.nodeCount / 2) {
                return LEADER
            }
        } else if (e.electionTerm > node.currentElectionTerm.number) {
            node.setCurrentElectionTerm(e.electionTerm)
            return FOLLOWER
        } else {
            logger.info("Received vote for wrong election term $e")
        }
        return CANDIDATE
    }
}
