package me.andresp.models

import org.mapdb.DBMaker
import org.mapdb.HTreeMap
import org.mapdb.Serializer

class StateInMemory : State {
    private val map: HTreeMap<String, String>

    init {
        val db = DBMaker.memoryDB().make()
        map = db.hashMap("state", Serializer.STRING, Serializer.STRING).createOrOpen()
    }

    override fun printAll() = map.forEach{ println("${it.key}: ${it.value}") }

    override fun get(key: String) = map[key]

    override fun set(key: String, value: String) {
        map.put(key, value)
    }

    override fun del(key: String) {
        map.remove(key)
    }
}

