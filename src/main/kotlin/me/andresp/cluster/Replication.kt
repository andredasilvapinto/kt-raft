package me.andresp.cluster

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch


fun replicate(node: Node, f: suspend (NodeAddress) -> Boolean): Deferred<Boolean> {
    val channel = Channel<Boolean>()
    val clusterNodes = node.cluster.nodeAddresses
    val others = clusterNodes.minus(node.nodeAddress)

    others.map {
        launch {
            channel.send(f(it))
        }
    }

    return async {
        var nSuccess = 1  // self
        val majority = clusterNodes.size / 2 + 1
        repeat(others.size) {
            val res = channel.receive()
            if (res) {
                nSuccess++
                if (nSuccess > majority) {
                    return@async true
                }
            }
        }
        return@async others.isEmpty()
    }
}
