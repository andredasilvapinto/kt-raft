package me.andresp.api

import me.andresp.cluster.NodeAddress

data class AskVotePayload(val electionTerm: Int, val candidateAddress: NodeAddress, val lastLogIndex: Int?, val lastLogTerm: Int?)

data class NodeJoinedPayload(val joinerAddress: NodeAddress, val forwarded: Boolean = false)

data class AppendEntriesReply(val electionTerm: Int, val success: Boolean, val matchIndex: Int)

data class ClientRedirect(val leaderAddress: NodeAddress?)
