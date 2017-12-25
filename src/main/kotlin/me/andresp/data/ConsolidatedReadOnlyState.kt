package me.andresp.data

interface ConsolidatedReadOnlyState {
    override fun toString(): String

    fun log()

    fun get(key: String): String?
}
