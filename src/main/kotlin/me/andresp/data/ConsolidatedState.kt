package me.andresp.data

interface ConsolidatedState : ConsolidatedReadOnlyState {
    fun set(key: String, value: String)

    fun del(key: String)
}
