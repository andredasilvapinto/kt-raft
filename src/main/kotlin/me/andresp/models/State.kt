package me.andresp.models

interface State : ReadOnlyState {
    fun set(key: String, value: String)

    fun del(key: String)
}
