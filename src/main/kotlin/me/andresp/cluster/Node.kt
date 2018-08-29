package me.andresp.cluster


class Node(val nodeAddress: NodeAddress, val cluster: Cluster) {
}

data class NodeAddress(val host: String, val port: Int)
