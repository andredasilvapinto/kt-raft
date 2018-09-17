package me.andresp

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import com.natpryce.konfig.parseArgs
import me.andresp.cluster.Cluster
import me.andresp.cluster.Node
import me.andresp.cluster.NodeAddress
import me.andresp.config.config
import me.andresp.data.*
import me.andresp.http.LOCAL_IP
import me.andresp.http.NodeClient
import me.andresp.http.startServer
import me.andresp.statemachine.StateMachine
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit


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

    logger.info("Log path: $logPath")
    // TODO If log persistence broken then break

    val log = LogDiskEhCache(logPath)
    val state = InMemoryConsolidatedState()

    val cmdProcessor = CommandProcessor(log, state)
    cmdProcessor.init()

    logger.info("State: $state")
    logger.info("Log: $log")

    val selfAddress = NodeAddress(LOCAL_IP, cfg[config.httpPort])
    val cluster = Cluster(cfg[config.numberNodes])
    val node = Node(selfAddress, cluster)

    val nodeClient = NodeClient.defaultClient(selfAddress)
    val stateMachine = StateMachine.construct(node, nodeClient, cmdProcessor)
    val server = startServer(httpPort, stateMachine, state, node)

    try {
        server.start(wait = false)
        stateMachine.start(target)
    } catch (e: Throwable) {
        server.stop(1000L, 1000L, TimeUnit.MILLISECONDS)
        log.close()
    }
}
