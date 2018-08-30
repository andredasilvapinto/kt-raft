package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient

// State Machine state (don't confuse with State as in data state - that is stored inside node)
abstract class AState(val id: StateId, protected val node: Node, protected val client: NodeClient) {
    open fun enter(stateMachine: StateMachine) {}

    abstract fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId

    protected fun handleLeaderHeartBeat(e: LeaderHeartbeat): StateId =
            if (e.electionTerm > node.currentElectionTerm.number) {
                node.cluster.updateLeader(e.leaderAddress, e.electionTerm, node.currentElectionTerm.number)
                StateId.FOLLOWER
            } else {
                id
            }

    protected fun handleNodeJoined(e: NodeJoined): StateId {
        node.cluster.addNode(e.joinerAddress)
        // TODO send only to old nodes?? or not send at all and wait till time out?
        //cluster.nodeAddresses.map { client.sendClusterStatus(it, cluster.getStatus()) }
        return id
    }

    protected fun handleVoteRequested(e: VoteRequested): StateId {
        // TODO: Implement voting logic with log index. Improve thread safety
        val reply = e.askVotePayload.electionTerm >= node.currentElectionTerm.number && node.currentElectionTerm.votedFor == null
        e.reply(AskVoteReply(node.currentElectionTerm.number, node.nodeAddress, reply))
        return StateId.FOLLOWER
    }
}

enum class StateId {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
