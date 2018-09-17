package me.andresp.data

class CommandProcessor(val log: Log, private val consolidatedState: ConsolidatedState) {

    // TODO: We can only apply the already committed log entries
    fun init() = applyAll(log)

    fun apply(cmd: Command) = when (cmd.type) {
        CommandType.SET -> consolidatedState.set(cmd.key, cmd.value!!)
        CommandType.DELETE -> consolidatedState.del(cmd.key)
    }

    fun applyAll(log: Log) = log.commands().forEach { apply(it) }
}
