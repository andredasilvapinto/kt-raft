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
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import me.andresp.events.CommandProcessor
import me.andresp.data.ConsolidatedReadOnlyState
import me.andresp.data.newDelete
import me.andresp.data.newSet

data class Item(val key: String, val value: String)
data class ItemValue(val value: String)

fun startServer(httpPort: Int, cmdProcessor: CommandProcessor, stateConsolidated: ConsolidatedReadOnlyState) {
    embeddedServer(Netty, httpPort) {
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
