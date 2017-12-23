package me.andresp.models

interface ReadOnlyState {
    override fun toString(): String

    fun log()

    fun get(key: String): String?
}
