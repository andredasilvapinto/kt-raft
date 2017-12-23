package me.andresp.models

interface Log {

    fun printAll()

    fun commands(): List<Command>

    fun append(cmd: Command)
}
