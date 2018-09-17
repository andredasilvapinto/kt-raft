package me.andresp.data

import org.mapdb.DBMaker
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import org.slf4j.LoggerFactory

class InMemoryConsolidatedState : ConsolidatedState {
    private val map: HTreeMap<String, String>

    init {
        val db = DBMaker.memoryDB().make()
        map = db.hashMap("state", Serializer.STRING, Serializer.STRING).createOrOpen()
    }

    override fun toString() = map.asIterable().joinToString { "${it.key}: ${it.value}" }

    override fun get(key: String) = map[key]

    override fun set(key: String, value: String) {
        map[key] = value
    }

    override fun del(key: String) {
        map.remove(key)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}

