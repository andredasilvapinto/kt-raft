package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LeaderState(private val node: Node, client: NodeClient) : AState(LEADER, client) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LeaderState::class.java)
    }

    override suspend fun <T : Event> handle(e: T): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, node.cluster)
            is NodeJoined -> handleNodeJoined(e, node.cluster)
            else -> {
                logger.info("FollowerState doesn't handle ${e.javaClass}. Ignoring.")
                LEADER
            }
        }
    }
}
