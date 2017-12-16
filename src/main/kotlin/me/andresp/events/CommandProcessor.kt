package me.andresp.events

import me.andresp.models.*

class CommandProcessor(private val log: Log, private val state: State) {
    fun apply(cmd: Command) {
        log.append(cmd)

        when (cmd.type) {
            CommandType.SET -> state.set(cmd.key, cmd.value!!)
            CommandType.DELETE -> state.del(cmd.key)
        }
    }

    fun applyAll(log: Log) {
        log.commands().forEach { apply(it) }
    }
}
