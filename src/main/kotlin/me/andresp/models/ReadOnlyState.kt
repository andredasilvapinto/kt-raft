package me.andresp.models

interface ReadOnlyState {
    fun printAll()

    fun get(key: String): String?
}
