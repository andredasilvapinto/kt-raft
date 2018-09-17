package me.andresp.data

interface ConsolidatedReadOnlyState {
    override fun toString(): String

    fun get(key: String): String?
}
