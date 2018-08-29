package me.andresp.http

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress
import me.andresp.data.CommandProcessor
import me.andresp.data.ConsolidatedReadOnlyState
import me.andresp.data.newDelete
import me.andresp.data.newSet
import me.andresp.statemachine.ClusterUpdated
import me.andresp.statemachine.NodeJoined
import me.andresp.statemachine.StateMachine
import me.andresp.statemachine.VoteReceived

data class Item(val key: String, val value: String)
data class ItemValue(val value: String)

fun startServer(httpPort: Int, stateMachine: StateMachine, cmdProcessor: CommandProcessor, stateConsolidated: ConsolidatedReadOnlyState) {
    embeddedServer(Netty, httpPort) {
        val logger = environment.log
        install(DefaultHeaders)
        install(Compression)
        install(CallLogging)
        install(ContentNegotiation) {
            jackson {
                configure(SerializationFeature.INDENT_OUTPUT, true)
            }
        }
        routing {
            get("/data/{key}") {
                val key = call.parameters["key"]!!
                val value = stateConsolidated.get(key)
                if (value == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(Item(key, value))
                }
            }
            put("/cluster/join") {
                val joinerAddress = call.receive<NodeAddress>()
                logger.info("Received join request from $joinerAddress")
                val nodeJoined = NodeJoined(joinerAddress)
                stateMachine.handle(nodeJoined)
                call.respond(HttpStatusCode.OK, stateMachine.node.cluster.getStatus())
            }
            post("/cluster/vote/{term}") {
                val electionTerm: Int = call.parameters["term"]!!.toInt()
                val voterAddress = call.receive<NodeAddress>()
                logger.info("Received vote from $voterAddress")
                val voteReceived = VoteReceived(electionTerm, voterAddress)
                stateMachine.handle(voteReceived)
            }
            put("/cluster/update") {
                val clusterStatus = call.receive<ClusterStatus>()
                logger.info("Received cluster status $clusterStatus")
                val clusterUpdated = ClusterUpdated(clusterStatus)
                stateMachine.handle(clusterUpdated)
            }
            post("/data/{key}") {
                val key = call.parameters["key"]!!
                val itemValue = call.receive<ItemValue>()
                cmdProcessor.apply(newSet(key, itemValue.value))
                stateConsolidated.log()
                call.respond(HttpStatusCode.OK)
            }
            delete("/data/{key}") {
                val key = call.parameters["key"]!!
                cmdProcessor.apply(newDelete(key))
                stateConsolidated.log()
                call.respond(HttpStatusCode.OK)
            }
        }
    }.start(wait = true)
}
