package me.andresp.events

import me.andresp.data.*

class CommandProcessor(private val log: Log, private val consolidatedState: ConsolidatedState) {
    fun apply(cmd: Command) {
        log.append(cmd)

        when (cmd.type) {
            CommandType.SET -> consolidatedState.set(cmd.key, cmd.value!!)
            CommandType.DELETE -> consolidatedState.del(cmd.key)
        }
    }

    fun applyAll(log: Log) {
        log.commands().forEach { apply(it) }
    }
}
