package me.andresp.statemachine

import me.andresp.statemachine.StateId.CANDIDATE
import me.andresp.statemachine.StateId.FOLLOWER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FollowerState(private val node: Node) : State(FOLLOWER) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FollowerState::class.java)
    }

    override fun <T : Event> handle(e: T): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, node)
            is LeaderHeartbeatTimeout -> CANDIDATE
            else -> {
                logger.info("FollowerState doesn't handle ${e.javaClass}. Ignoring.")
                CANDIDATE
            }
        }
    }
}
