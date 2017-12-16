package me.andresp.models

interface State {
    fun print()

    fun set(key: String, value: String)

    fun del(key: String)
}
