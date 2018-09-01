package me.andresp.api

import me.andresp.cluster.NodeAddress

data class AskVotePayload(val electionTerm: Int, val candidateAddress: NodeAddress, val lastLogIndex: Long, val lastLogTerm: Int)

data class NodeJoinedPayload(val joinerAddress: NodeAddress, val forwarded: Boolean = false)

data class AppendEntriesReply(val electionTerm: Int, val success: Boolean)
