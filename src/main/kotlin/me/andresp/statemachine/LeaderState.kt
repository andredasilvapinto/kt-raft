package me.andresp.statemachine

import kotlinx.coroutines.experimental.runBlocking
import me.andresp.cluster.Node
import me.andresp.cluster.replicate
import me.andresp.data.Command
import me.andresp.data.CommandProcessor
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.schedule

class LeaderState(
        node: Node,
        client: NodeClient,
        cmdProcessor: CommandProcessor
) : AState(LEADER, node, client, cmdProcessor) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(LeaderState::class.java)
        private const val LEADER_HEARTBEAT_PERIOD_MS = 500L
    }

    private val timer = Timer(true)
    private var timerTask: TimerTask? = null

    override fun enter(stateMachine: StateMachine) {
        node.handleNewLeaderTerm(node.nodeAddress, node.currentElectionTerm.number)
        stateMachine.cancelElectionTimeout()
        scheduleHeartbeat()
    }

    private fun scheduleHeartbeat() {
        timerTask?.cancel()
        timerTask = timer.schedule(0, LEADER_HEARTBEAT_PERIOD_MS) {
            client.broadcast(node.cluster) {
                logger.info("Sending Leader Heartbeat...")
                val leaderHearbeat = LeaderHeartbeat(
                        node.currentElectionTerm.number,
                        node.nodeAddress,
                        node.cluster.getStatus()
                )
                client.sendHeartbeat(it, leaderHearbeat)
            }
        }
    }

    private fun cancelHeartbeat() {
        logger.info("Cancelling leader heartbeats")
        timerTask?.cancel()
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        val newStateId = when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, stateMachine)
            is NodeJoinedRequest -> handleNodeJoined(e)
            is VoteRequested -> handleVoteRequested(e)
            is Command -> handleClientCommand(e)
            is AppendEntriesWrapper -> handleAppendEntry(e)
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

    private fun handleClientCommand(cmd: Command): StateId {
        val log = cmdProcessor.log

        // persist
        log.append(cmd)

        val lastCommittedIndex = log.lastIndex()
        val lastCommittedCommand = log.last()
        val appendEntry = AppendEntries(node.currentElectionTerm.number, node.nodeAddress, lastCommittedIndex, lastCommittedCommand?.termNumber, listOf(cmd), lastCommittedIndex)

        val replicationJob = replicate(node) {
            // TODO previous log index should be the last current index and not the last committed index?
            // TODO infinite retries (unless leader reverted for the term before committing entry)
            client.sendAppendEntry(it, appendEntry).success
        }

        // TODO: infinite retries (until no leader swap before command is committed)
        runBlocking {
            val result = replicationJob.await()
            if (result) {
                logger.info("Replication successful for $appendEntry")
                cmdProcessor.apply(cmd)
            } else {
                // TODO improve error logging
                logger.error("Failed to replicate $appendEntry to a majority of the nodes")
            }
        }

        return LEADER
    }
}
