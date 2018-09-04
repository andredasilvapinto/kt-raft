package me.andresp.data

import java.io.Closeable

interface Log: Closeable {

    override fun toString(): String

    fun log()

    fun commands(): List<Command>

    fun append(cmd: Command)
}
