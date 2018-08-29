package me.andresp.cluster

class Cluster(val totalNumberOfNodes: Int) {
    var leader: NodeAddress? = null
        private set
    // needs to be persisted to disk
    var currentElectionTerm = 1
        private set
    val nodeAddresses = mutableSetOf<NodeAddress>()

    fun updateLeader(newLeader: NodeAddress, newLeaderElectionTerm: Int) {
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

    fun addNode(newNode: NodeAddress) = nodeAddresses.add(newNode)

    fun getStatus() = ClusterStatus(nodeAddresses)
}

data class ClusterStatus(val nodes: Set<NodeAddress>)
