package me.andresp.http

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.config.MapApplicationConfig
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.jackson.jackson
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

fun Application.main() {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(ContentNegotiation) {
        jackson {
            configure(SerializationFeature.INDENT_OUTPUT, true)
        }
    }
    embeddedServer(Netty, 8080) {
        routing {
            get("/data/{key}") {
                val key = call.parameters["key"]
                call.respondText("$key", ContentType.Text.Plain)
            }
        }
    }.start(wait = true)
}

class ApplicationEnv : ApplicationEnvironment {
    override val classLoader = ClassLoader.getSystemClassLoader()!!
    override val config = MapApplicationConfig()
    override val log = LoggerFactory.getLogger("MyApp")!!
    override val monitor = ApplicationEvents()
}

fun main(args: Array<String>) {
    Application(ApplicationEnv()).main()
}
