package me.andresp.models

interface Log {

    fun print()

    fun commands(): List<Command>

    fun append(cmd: Command)
}
