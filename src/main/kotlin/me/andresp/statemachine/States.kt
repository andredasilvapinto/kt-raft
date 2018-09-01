package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient

// State Machine state (don't confuse with State as in data state - that is stored inside node)
abstract class AState(val stateId: StateId, protected val node: Node, protected val client: NodeClient) {
    open fun enter(stateMachine: StateMachine) {}

    abstract fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId

    protected fun handleLeaderHeartBeat(e: LeaderHeartbeat, stateMachine: StateMachine): StateId =
            if (e.electionTerm > node.currentElectionTerm.number) {
                node.handleNewLeader(e.leaderAddress, e.electionTerm)
                stateMachine.scheduleElectionTimeout()
                StateId.FOLLOWER
            } else {
                if (e.leaderAddress == node.cluster.leader) {
                    // reset timeout
                    stateMachine.scheduleElectionTimeout()
                }
                stateId
            }

    protected open fun handleNodeJoined(e: NodeJoinedRequest): StateId {
        if (!e.payload.forwarded) {
            client.broadcastAndWait(node.cluster) {
                client.join(it, e.payload.copy(forwarded = true))
            }
        }

        node.cluster.addNode(e.payload.joinerAddress)

        e.reply(node.cluster.getStatus())

        return stateId
    }

    protected fun handleVoteRequested(e: VoteRequested): StateId {
        // TODO: Implement voting logic with log index. Improve thread safety
        val newerTerm = e.askVotePayload.electionTerm > node.currentElectionTerm.number
        val sameTerm = e.askVotePayload.electionTerm == node.currentElectionTerm.number
        val noVoteYet = node.currentElectionTerm.votedFor == null

        if (newerTerm) {
            node.setCurrentElectionTerm(e.askVotePayload.electionTerm)
        }
        val reply = newerTerm || (sameTerm && noVoteYet)

        e.reply(AskVoteReply(node.currentElectionTerm.number, node.nodeAddress, reply))

        return if (reply) StateId.FOLLOWER else stateId
    }
}

enum class StateId {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
