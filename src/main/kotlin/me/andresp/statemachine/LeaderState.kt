package me.andresp.statemachine

import me.andresp.statemachine.StateId.LEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LeaderState(private val node: Node) : State(LEADER) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LeaderState::class.java)
    }

    override fun <T : Event> handle(e: T): StateId {
        return when (e) {
            is LeaderHeartbeat -> handleLeaderHeartBeat(e, node)
            else -> {
                logger.info("FollowerState doesn't handle ${e.javaClass}. Ignoring.")
                LEADER
            }
        }
    }
}
