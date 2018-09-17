package me.andresp.data

import kotlinx.serialization.Serializable
import me.andresp.statemachine.Event

enum class CommandType { SET, DELETE }

@Serializable
data class Command(val type: CommandType, val termNumber: Int, val key: String, val value: String?) : Event

fun newSet(termNumber: Int, key: String, value: String) = Command(CommandType.SET, termNumber, key, value)
fun newDelete(termNumber: Int, key: String) = Command(CommandType.DELETE, termNumber, key, null)
