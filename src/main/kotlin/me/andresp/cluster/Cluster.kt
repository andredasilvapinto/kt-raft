package me.andresp.cluster

class Cluster(val totalNumberOfNodes: Int) {
    @Volatile
    var nodeAddresses = mutableSetOf<NodeAddress>()
        private set

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
