package me.andresp

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import com.natpryce.konfig.parseArgs
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JsonFeature
import me.andresp.cluster.NodeAddress
import me.andresp.config.config
import me.andresp.data.*
import me.andresp.http.NodeClient
import me.andresp.http.startServer
import me.andresp.statemachine.StateMachine
import org.slf4j.LoggerFactory
import java.io.File


val logger = LoggerFactory.getLogger("main")!!

fun main(args: Array<String>) {
    val (cfg, _) = parseArgs(args,
            CommandLineOption(config.target, "target", "t", "address to connect to", "IP:PORT"),
            CommandLineOption(config.httpPort, "httpPort", "p", "port to listen on", "PORT")
    ) overriding ConfigurationProperties.fromResource("config.properties")
    logger.info(cfg.list().toString())

    val httpPort = cfg[config.httpPort]
    val numberOfNodes = cfg[config.numberNodes]
    val target = cfg.getOrNull(config.target)?.let {
        val (tAddress, tPort) = it.split(":")
        NodeAddress(tAddress, tPort.toInt())
    }
    val logPath = cfg[config.logPath]

    logger.info("$httpPort, $numberOfNodes, $logPath, $target")

    logger.info("Creating new log at: $logPath")
    File(logPath).deleteRecursively()

    val log = LogDisk(logPath)
    val state = InMemoryConsolidatedState()

    val cmdProcessor = CommandProcessor(log, state)
    cmdProcessor.apply(newSet("A", "3"))
    cmdProcessor.apply(newSet("B", "5"))
    cmdProcessor.apply(newDelete("A"))

    state.log()
    log.log()

    val nodeClient = NodeClient(HttpClient(Apache) { install(JsonFeature)})
    val stateMachine = StateMachine.construct(cfg, nodeClient)
    stateMachine.start(target)
    startServer(httpPort, stateMachine, cmdProcessor, state)
}
