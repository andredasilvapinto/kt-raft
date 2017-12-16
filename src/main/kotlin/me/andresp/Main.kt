package me.andresp

import me.andresp.events.CommandProcessor
import me.andresp.models.LogDisk
import me.andresp.models.StateInMemory
import me.andresp.models.newDelete
import me.andresp.models.newSet
import java.io.File


fun main(args: Array<String>) {
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
