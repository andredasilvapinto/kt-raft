package me.andresp.statemachine

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.andresp.api.AskVotePayload
import me.andresp.cluster.Node
import me.andresp.cluster.NodeAddress
import me.andresp.data.CommandProcessor
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CandidateState(
        node: Node,
        client: NodeClient,
        cmdProcessor: CommandProcessor
) : AState(CANDIDATE, node, client, cmdProcessor) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DisconnectedState::class.java)
    }

    @Volatile
    private lateinit var candidacy: Candidacy

    override fun enter(stateMachine: StateMachine) {
        startElection(stateMachine)
    }

    override fun leave(stateMachine: StateMachine) {
        candidacy.stop()
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        return when (e) {
            is ElectionTimeout -> startElection(stateMachine)
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, stateMachine)
            is VoteReceived -> handleVoteReceived(e)
            is NodeJoinedRequest -> handleNodeJoined(e)
            is VoteRequested -> handleVoteRequested(e)
            is AppendEntriesWrapper -> handleAppendEntry(e)
            else -> {
                logger.info("Candidate doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }

    private fun startElection(stateMachine: StateMachine): StateId {
        node.newTerm()
        stateMachine.scheduleElectionTimeout()
        if (this::candidacy.isInitialized) {
            candidacy.stop()
        }

        val term = node.currentElectionTerm.number
        val log = cmdProcessor.log
        val askVotePayload = AskVotePayload(term, node.nodeAddress, log.lastIndex(), log.last()?.termNumber)

        candidacy = Candidacy(term, stateMachine, client, node, askVotePayload)

        candidacy.askForVotes()

        return CANDIDATE
    }

    private fun handleVoteReceived(e: VoteReceived): StateId =
            if (e.electionTerm == node.currentElectionTerm.number) {
                if (e.electionTerm == candidacy.term) {
                    candidacy.handleVoteReceived(e)
                    if (candidacy.totalVotes > node.cluster.nodeCount / 2) {
                        LEADER
                    } else {
                        CANDIDATE
                    }
                } else {
                    // Should never happen
                    throw IllegalStateException("Candidacy object is outdated: ${candidacy.term} vs ${e.electionTerm}")
                }
            } else if (node.updateTermIfNewer(e.electionTerm)) {
                FOLLOWER
            } else {
                logger.info("Received vote for old election term $e. Ignoring.")
                CANDIDATE
            }
}

private class Candidacy(
        val term: Int,
        private val stateMachine: StateMachine,
        private val client: NodeClient,
        private val node: Node,
        private val askVotePayload: AskVotePayload
) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Candidacy::class.java)
        const val VOTE_REQUEST_TIMEOUT_MS = 50000L // 500L
    }

    private val receivedVotes = mutableSetOf<NodeAddress>()

    @Volatile
    private var active = true

    val totalVotes: Int
        get() = receivedVotes.size

    fun askForVotes() {
        GlobalScope.launch {
            // Vote for itself
            // needs to be done in a different coroutine so the synchronized handle
            // blocks this from running before the current event is completely processed
            stateMachine.handle(VoteReceived(term, node.nodeAddress))
        }

        client.broadcast(node.cluster) {
            while (active) {
                try {
                    logger.info("Asking $it for a vote")
                    val reply = client.sendRequestForVote(it, askVotePayload)
                    if (reply.voteGranted) {
                        stateMachine.handle(VoteReceived(term, reply.voterAddress))
                    }
                    break
                } catch (e: Exception) {
                    logger.error("Error asking for votes from $it", e)
                    delay(VOTE_REQUEST_TIMEOUT_MS) // TODO Implement exponential backoff
                }
            }
        }
    }

    fun handleVoteReceived(e: VoteReceived) {
        receivedVotes.add(e.voterAddress)
    }

    fun stop() {
        if (active) {
            logger.info("Stopping candidacy $term")
        }
        active = false
    }
}
