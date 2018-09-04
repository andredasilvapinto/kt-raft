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
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import me.andresp.api.AppendEntriesReply
import me.andresp.api.AskVotePayload
import me.andresp.api.NodeJoinedPayload
import me.andresp.data.CommandProcessor
import me.andresp.data.ConsolidatedReadOnlyState
import me.andresp.data.newDelete
import me.andresp.data.newSet
import me.andresp.statemachine.*

data class Item(val key: String, val value: String)
data class ItemValue(val value: String)

fun startServer(httpPort: Int, stateMachine: StateMachine, cmdProcessor: CommandProcessor, stateConsolidated: ConsolidatedReadOnlyState): ApplicationEngine {
    return embeddedServer(Netty, httpPort) {
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
            put("/cluster/join") { it ->
                val nodeJoinedPayload = call.receive<NodeJoinedPayload>()
                logger.info("Received join request $nodeJoinedPayload")
                val nodeJoinedRequest = NodeJoinedRequest(nodeJoinedPayload) {
                    runBlocking { call.respond(HttpStatusCode.OK, it) }
                }
                stateMachine.handle(nodeJoinedRequest)
            }
            put("/cluster/ask-vote/{term}") { _ ->
                val askVotePayload = call.receive<AskVotePayload>()
                logger.info("Received vote request $askVotePayload")
                val voteRequested = VoteRequested(askVotePayload) {
                    runBlocking { call.respond(HttpStatusCode.OK, it) }
                }
                stateMachine.handle(voteRequested)
            }
//            post("/cluster/vote/{term}") {
//                val electionTerm: Int = call.parameters["term"]!!.toInt()
//                val leaderAddress = call.receive<NodeAddress>()
//                logger.info("Received vote from $leaderAddress")
//                val voteReceived = VoteReceived(electionTerm, leaderAddress)
//                stateMachine.handle(voteReceived)
//            }
            put("/cluster/heartbeat") {
                val leaderHeartbeat = call.receive<LeaderHeartbeat>()
                logger.info("Received heartbeat $leaderHeartbeat")
                stateMachine.handle(leaderHeartbeat)
                // TODO: Implement a better way to handle RPC replies (maybe multiple return on handle()?). Hardcoded replies for now.
                call.respond(HttpStatusCode.OK, AppendEntriesReply(stateMachine.node.currentElectionTerm.number, true, 0))
            }
            get("/data/{key}") {
                val key = call.parameters["key"]!!
                val value = stateConsolidated.get(key)
                if (value == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(Item(key, value))
                }
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
    }
}
