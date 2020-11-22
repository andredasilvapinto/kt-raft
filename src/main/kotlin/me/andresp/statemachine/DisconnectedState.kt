package me.andresp.statemachine

import me.andresp.cluster.Node
import me.andresp.data.CommandProcessor
import me.andresp.http.NodeClient
import me.andresp.statemachine.StateId.DISCONNECTED
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DisconnectedState(
        node: Node,
        client: NodeClient,
        cmdProcessor: CommandProcessor
) : AState(DISCONNECTED, node, client, cmdProcessor) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DisconnectedState::class.java)
    }

    override fun <T : Event> handle(e: T, stateMachine: StateMachine): StateId {
        logger.info("In disconnected state. Ignoring ${e.javaClass}.")
        return DISCONNECTED
    }
}
