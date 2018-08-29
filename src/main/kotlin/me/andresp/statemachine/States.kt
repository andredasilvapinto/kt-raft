package me.andresp.statemachine

import me.andresp.cluster.Cluster
import me.andresp.http.NodeClient

// State Machine state (don't confuse with State as in data state - that is stored inside node)
abstract class AState(val id: StateId, protected val client: NodeClient) {
    open fun enter() {}

    abstract suspend fun <T : Event> handle(e: T): StateId

    fun handleLeaderHeartBeat(e: LeaderHeartbeat, cluster: Cluster): StateId =
            if (e.electionTerm > cluster.currentElectionTerm) {
                cluster.updateLeader(e.leaderAddress, e.electionTerm)
                StateId.FOLLOWER
            } else {
                id
            }

    fun handleNodeJoined(e: NodeJoined, cluster: Cluster): StateId {
        cluster.addNode(e.joinerAddress)
        // TODO send only to old nodes?? or not send at all and wait till time out?
        //cluster.nodeAddresses.map { client.sendClusterStatus(it, cluster.getStatus()) }
        return id
    }
}

enum class StateId {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
