package me.andresp.statemachine

import me.andresp.api.AppendEntriesReply
import me.andresp.api.AskVotePayload
import me.andresp.api.NodeJoinedPayload
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress
import me.andresp.data.Command

interface Event

data class LeaderHeartbeat(val electionTerm: Int, val leaderAddress: NodeAddress, val clusterStatus: ClusterStatus) : Event
data class ElectionTimeout(val electionTerm: Int) : Event

data class VoteReceived(val electionTerm: Int, val voterAddress: NodeAddress) : Event


data class AskVoteReply(val electionTerm: Int, val voterAddress: NodeAddress, val voteGranted: Boolean)
typealias VoteGrantedReply = (AskVoteReply) -> Unit

data class VoteRequested(val askVotePayload: AskVotePayload, val reply: VoteGrantedReply) : Event


typealias NodeJoinedReply = (ClusterStatus) -> Unit

data class NodeJoinedRequest(val payload: NodeJoinedPayload, val reply: NodeJoinedReply) : Event


data class AppendEntries(val leaderTerm: Int, val leaderAddress: NodeAddress, val prevLogIndex: Int?, val prevLogTermNumber: Int?, val entries: List<Command>, val leaderCommitIndex: Int?)
typealias AppendEntriesReplyWrapper = (AppendEntriesReply) -> Unit

data class AppendEntriesWrapper(val appendEntries: AppendEntries, val reply: AppendEntriesReplyWrapper) : Event
