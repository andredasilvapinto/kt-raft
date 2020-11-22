package me.andresp.cluster

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch


fun replicate(node: Node, f: suspend (NodeAddress) -> Boolean): Deferred<Boolean> {
    val channel = Channel<Boolean>()
    val clusterNodes = node.cluster.nodeAddresses
    val others = clusterNodes.minus(node.nodeAddress)

    others.map {
        GlobalScope.launch {
            channel.send(f(it))
        }
    }

    return GlobalScope.async {
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
