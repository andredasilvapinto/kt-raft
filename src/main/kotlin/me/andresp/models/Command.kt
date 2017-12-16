package me.andresp.models

import kotlinx.serialization.Serializable

enum class CommandType { SET, DELETE }

@Serializable
data class Command(val type: CommandType, val key: String, val value: String?)

fun newSet(key: String, value: String) = Command(CommandType.SET, key, value)
fun newDelete(key: String) = Command(CommandType.DELETE, key, null)
