package me.andresp.statemachine

import me.andresp.api.AppendEntriesReply
import me.andresp.cluster.Node
import me.andresp.data.CommandProcessor
import me.andresp.http.NodeClient

// State Machine state (don't confuse with State as in data state - that is stored inside node)
abstract class AState(
        val stateId: StateId,
        protected val node: Node,
        protected val client: NodeClient,
        protected val cmdProcessor: CommandProcessor
) {
    open fun enter(stateMachine: StateMachine) {}

    open fun leave(stateMachine: StateMachine) {}

    abstract fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId

    protected fun handleLeaderHeartBeat(e: LeaderHeartbeat, stateMachine: StateMachine): StateId =
            if (e.electionTerm >= node.currentElectionTerm.number) {
                node.handleNewLeaderTerm(e.leaderAddress, e.electionTerm)
                stateMachine.scheduleElectionTimeout()
                node.cluster.setStatus(e.clusterStatus)
                StateId.FOLLOWER
            } else {
                stateId
            }

    protected open fun handleNodeJoined(e: NodeJoinedRequest): StateId {
        if (!e.payload.forwarded) {
            // TODO nodes should initially be in non-voting state
            // Do not redirect node joined event to the joining node
            // (in case it is rejoining a cluster after disconnecting)
            client.broadcastAndWait(node.cluster.nodeAddresses.minus(e.payload.joinerAddress)) {
                client.sendJoinNotification(it, e.payload.copy(forwarded = true))
            }
        }

        node.cluster.addNode(e.payload.joinerAddress)

        e.reply(node.cluster.getStatus())

        return stateId
    }

    protected fun handleVoteRequested(e: VoteRequested): StateId {
        // TODO: Improve thread safety ?
        val askVotePayload = e.askVotePayload
        val newerTerm = node.updateTermIfNewer(askVotePayload.electionTerm)
        val sameTerm = askVotePayload.electionTerm == node.currentElectionTerm.number
        val noVoteYet = node.currentElectionTerm.votedFor == null
        val log = cmdProcessor.log

        val candidateLastLogTerm = askVotePayload.lastLogTerm ?: -1
        val candidateLastLogIndex = askVotePayload.lastLogIndex ?: -1
        val myLastLogTerm = log.last()?.termNumber ?: -1
        val myLastLogIndex = log.lastIndex() ?: -1
        val recentLog = candidateLastLogTerm >= myLastLogTerm
                && candidateLastLogIndex >= myLastLogIndex

        val reply = newerTerm || (sameTerm && noVoteYet) && recentLog

        e.reply(AskVoteReply(node.currentElectionTerm.number, node.nodeAddress, reply))

        return if (reply) {
            node.currentElectionTerm.votedFor = askVotePayload.candidateAddress
            StateId.FOLLOWER
        } else {
            stateId
        }
    }

    protected fun handleAppendEntry(e: AppendEntriesWrapper): StateId {
        // TODO check leaderCommitIndex, prevLogTermNumber and leaderTerm
        // TODO update matchIndex
        e.reply(AppendEntriesReply(node.currentElectionTerm.number, true, 1))
        return stateId
    }
}

enum class StateId {
    DISCONNECTED,
    FOLLOWER,
    CANDIDATE,
    LEADER
}
