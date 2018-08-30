package me.andresp.cluster

class Cluster(val totalNumberOfNodes: Int) {
    var leader: NodeAddress? = null
        private set
    @Volatile
    var nodeAddresses = mutableSetOf<NodeAddress>()
        private set

    @Synchronized
    fun updateLeader(newLeader: NodeAddress) {
        // TODO Improve + persist
        this.leader = newLeader
    }

    fun addNode(newNode: NodeAddress) = nodeAddresses.add(newNode)

    fun getStatus() = ClusterStatus(nodeAddresses)
    fun setStatus(clusterStatus: ClusterStatus) {
        if (nodeAddresses != clusterStatus.nodes) {
            nodeAddresses = clusterStatus.nodes.toMutableSet()
        }
    }

    val nodeCount get() = nodeAddresses.size
}

data class ClusterStatus(val nodes: Set<NodeAddress>)
