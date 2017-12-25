package me.andresp

import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import com.natpryce.konfig.parseArgs
import me.andresp.config.config
import me.andresp.events.CommandProcessor
import me.andresp.http.startServer
import me.andresp.data.LogDisk
import me.andresp.data.InMemoryConsolidatedState
import me.andresp.data.newDelete
import me.andresp.data.newSet
import org.slf4j.LoggerFactory
import java.io.File


fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("main")

    val (cfg, _) = parseArgs(args, CommandLineOption(config.target, "target", "t", "address to connect to", "IP:PORT")) overriding
            ConfigurationProperties.fromResource("config.properties")
    logger.info(cfg.list().toString())

    val httpPort = cfg[config.httpPort]
    val numberOfNodes = cfg[config.numberNodes]
    val target = cfg.getOrNull(config.target)
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

    startServer(httpPort, cmdProcessor, state)
}
