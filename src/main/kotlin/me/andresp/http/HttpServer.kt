package me.andresp.http

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.ApplicationCall
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
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.runBlocking
import me.andresp.api.AppendEntriesReply
import me.andresp.api.AskVotePayload
import me.andresp.api.ClientRedirect
import me.andresp.api.NodeJoinedPayload
import me.andresp.cluster.Node
import me.andresp.data.ConsolidatedReadOnlyState
import me.andresp.data.newDelete
import me.andresp.data.newSet
import me.andresp.statemachine.*
import me.andresp.statemachine.StateId.LEADER

data class Item(val key: String, val value: String)
data class ItemValue(val value: String)

const val ENDPOINT_CLUSTER_JOIN = "/cluster/join"
const val ENDPOINT_CLUSTER_ASK_VOTE = "/cluster/ask-vote"
const val ENDPOINT_CLUSTER_HEARTBEAT = "/cluster/heartbeat"
const val ENDPOINT_LOG_APPEND = "/log/append"
const val ENDPOINT_KEY = "/data/{key}"

fun startServer(httpPort: Int, stateMachine: StateMachine, stateConsolidated: ConsolidatedReadOnlyState, node: Node): ApplicationEngine {
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
            put(ENDPOINT_CLUSTER_JOIN) { _ ->
                val nodeJoinedPayload = call.receive<NodeJoinedPayload>()
                logger.info("Received join request $nodeJoinedPayload")
                val nodeJoinedRequest = NodeJoinedRequest(nodeJoinedPayload) {
                    runBlocking {
                        call.respond(HttpStatusCode.OK, it)
                        logger.info("Responded to join request $nodeJoinedPayload with $it")
                    }
                }
                stateMachine.handle(nodeJoinedRequest)
            }
            put("$ENDPOINT_CLUSTER_ASK_VOTE/{term}") { _ ->
                val askVotePayload = call.receive<AskVotePayload>()
                logger.info("Received vote request $askVotePayload")
                val voteRequested = VoteRequested(askVotePayload) {
                    runBlocking { call.respond(HttpStatusCode.OK, it) }
                }
                stateMachine.handle(voteRequested)
            }
            put(ENDPOINT_CLUSTER_HEARTBEAT) {
                val leaderHeartbeat = call.receive<LeaderHeartbeat>()
                logger.info("Received heartbeat $leaderHeartbeat")
                stateMachine.handle(leaderHeartbeat)
                // TODO: Implement a better way to handle RPC replies (maybe multiple return on handle()?). Hardcoded replies for now.
                call.respond(HttpStatusCode.OK, AppendEntriesReply(stateMachine.node.currentElectionTerm.number, true, 0))
            }
            post(ENDPOINT_LOG_APPEND) { _ ->
                val appendEntry = call.receive<AppendEntries>()
                logger.info("Received AppendEntries $appendEntry")
                val appendEntryWrapper = AppendEntriesWrapper(appendEntry) {
                    runBlocking { call.respond(HttpStatusCode.OK, it) }
                }
                stateMachine.handle(appendEntryWrapper)
            }
            get(ENDPOINT_KEY) {
                val key = call.parameters["key"]!!
                val value = stateConsolidated.get(key)
                if (value == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(Item(key, value))
                }
            }
            post(ENDPOINT_KEY) {
                leaderFilter(stateMachine) {
                    val key = call.parameters["key"]!!
                    val itemValue = call.receive<ItemValue>()
                    stateMachine.handle(newSet(node.currentElectionTerm.number, key, itemValue.value))
                    logger.info("State: $stateConsolidated")
                    call.respond(HttpStatusCode.OK)
                }
            }
            delete(ENDPOINT_KEY) {
                leaderFilter(stateMachine) {
                    val key = call.parameters["key"]!!
                    stateMachine.handle(newDelete(node.currentElectionTerm.number, key))
                    logger.info("State: $stateConsolidated")
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

suspend fun PipelineContext<*, ApplicationCall>.leaderFilter(stateMachine: StateMachine, f: suspend () -> Unit) {
    if (stateMachine.currentState.stateId == LEADER) {
        f()
    } else {
        call.respond(HttpStatusCode.TemporaryRedirect, ClientRedirect(stateMachine.node.currentElectionTerm.leaderAddress))
    }
}
