package me.andresp.statemachine

interface Event

data class LeaderHeartbeat(val leader: String, val electionTerm: Int) : Event
data class LeaderHeartbeatTimeout(val electionTerm: Int) : Event
data class VoteReceived(val electionTerm: Int) : Event
