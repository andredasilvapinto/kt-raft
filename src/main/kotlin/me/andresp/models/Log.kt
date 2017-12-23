package me.andresp.models

interface Log {

    override fun toString(): String

    fun log()

    fun commands(): List<Command>

    fun append(cmd: Command)
}
