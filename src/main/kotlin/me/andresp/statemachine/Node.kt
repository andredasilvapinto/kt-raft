package me.andresp.statemachine


class Node(val totalNumberOfNodes: Int) {
    // TODO: Generate (consider using uris / ip:port)
    val nodeId = "something-1"
    var leader = ""
        private set
    // needs to be persisted to disk
    var currentElectionTerm = 1
        private set
    val otherNodes = mutableSetOf<NodeAddress>()

    fun updateLeader(newLeader: String, newLeaderElectionTerm: Int) {
        synchronized(this, {
            // TODO Improve + persist
            if (newLeaderElectionTerm < currentElectionTerm) {
                throw IllegalArgumentException("Trying to set older leader $newLeader from term $newLeaderElectionTerm when we already have $leader from term $currentElectionTerm")
            } else {
                this.leader = leader
                this.currentElectionTerm = currentElectionTerm
            }
        })
    }
}

data class NodeAddress(val host: String, val port: Int)
