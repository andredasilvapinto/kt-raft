package me.andresp.statemachine

import me.andresp.api.AskVotePayload
import me.andresp.api.NodeJoinedPayload
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress

interface Event

data class LeaderHeartbeat(val electionTerm: Int, val leaderAddress: NodeAddress, val clusterStatus: ClusterStatus) : Event
data class ElectionTimeout(val electionTerm: Int) : Event

data class VoteReceived(val electionTerm: Int, val voterAddress: NodeAddress) : Event

data class AskVoteReply(val electionTerm: Int, val voterAddress: NodeAddress, val voteGranted: Boolean)
typealias VoteGrantedReply = (AskVoteReply) -> Unit
data class VoteRequested(val askVotePayload: AskVotePayload, val reply: VoteGrantedReply) : Event

typealias NodeJoinedReply = (ClusterStatus) -> Unit
data class NodeJoinedRequest(val payload: NodeJoinedPayload, val reply: NodeJoinedReply) : Event

