package me.andresp.data

import java.io.Closeable

interface Log : Closeable {

    // TODO: Add committed index?

    override fun toString(): String

    fun commands(includePending: Boolean = false): List<Command>

    fun append(cmd: Command)

    fun lastIndex(): Int?

    fun get(i: Int): Command?

    fun last(): Command?

    fun isEmpty(): Boolean
}
