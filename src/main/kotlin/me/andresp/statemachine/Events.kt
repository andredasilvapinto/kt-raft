package me.andresp.statemachine

import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress

interface Event

data class LeaderHeartbeat(val electionTerm: Int, val leaderAddress: NodeAddress) : Event
data class LeaderHeartbeatTimeout(val electionTerm: Int) : Event
data class VoteReceived(val electionTerm: Int, val voterAddress: NodeAddress) : Event
data class VoteRequested(val electionTerm: Int, val candidateAddress: NodeAddress) : Event
data class NodeJoined(val joinerAddress: NodeAddress) : Event
data class ClusterUpdated(val clusterStatus: ClusterStatus) : Event
