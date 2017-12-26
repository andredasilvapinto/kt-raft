package me.andresp.statemachine

abstract class State(val id: StateId) {
    open fun enter() {}

    abstract fun <T : Event> handle(e: T): StateId

    fun handleLeaderHeartBeat(e: LeaderHeartbeat, node: Node): StateId =
            if (e.electionTerm > node.currentElectionTerm) {
                node.updateLeader(e.leader, e.electionTerm)
                StateId.FOLLOWER
            } else {
                id
            }
}

enum class StateId {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
