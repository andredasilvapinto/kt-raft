package me.andresp

import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.overriding
import me.andresp.config.config
import me.andresp.events.CommandProcessor
import me.andresp.models.LogDisk
import me.andresp.models.StateInMemory
import me.andresp.models.newDelete
import me.andresp.models.newSet
import java.io.File


fun main(args: Array<String>) {
    val cfg = EnvironmentVariables() overriding
            ConfigurationProperties.fromResource("config.properties")

    println(cfg.list())
    println("${cfg[config.httpPort]}, ${cfg[config.numberNodes]}")

    val filePath = "./queue.tape"

    println("Creating new log at: $filePath")
    File(filePath).deleteRecursively()

    val log = LogDisk(filePath)
    log.print()

    val state = StateInMemory()
    state.print()

    val cmdProcessor = CommandProcessor(log, state)
    cmdProcessor.apply(newSet("A", "3"))
    cmdProcessor.apply(newSet("B", "5"))
    cmdProcessor.apply(newDelete("A"))

    state.print()

    log.print()
}
