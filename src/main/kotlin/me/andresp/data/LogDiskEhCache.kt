package me.andresp.data

import io.ktor.util.moveToByteArray
import kotlinx.serialization.cbor.CBOR
import org.ehcache.Cache
import org.ehcache.PersistentCacheManager
import org.ehcache.Status
import org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.config.units.MemoryUnit
import org.ehcache.core.spi.service.FileBasedPersistenceContext
import org.ehcache.spi.serialization.Serializer
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger


class LogDiskEhCache(filePath: String) : Log {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    private val cacheManager: PersistentCacheManager
    private val cache: Cache<Int, Command>
    private val index: AtomicInteger = AtomicInteger()

    init {
        cacheManager = newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(File(filePath)))
                .withCache("log",
                        newCacheConfigurationBuilder(Int::class.javaObjectType, Command::class.java,
                                ResourcePoolsBuilder.newResourcePoolsBuilder().disk(10, MemoryUnit.MB, true))
                                .withValueSerializer(CommandSerializer::class.java)
                                .build())
                .build(true)

        cache = cacheManager.getCache("log", Int::class.javaObjectType, Command::class.java)

        Runtime.getRuntime().addShutdownHook(Thread { close() })
    }

    override fun append(cmd: Command) {
        index.getAndUpdate {
            cache.put(it, cmd)
            it + 1
        }
    }

    override fun toString() = cache.toList().fold("") { acc, cmd -> "$acc ${cmd.key} - ${cmd.value}, " }

    override fun log() = logger.info("Current log: $this")

    override fun commands() = cache.fold(arrayListOf<Command>()) { acc, e ->
        acc[e.key] = e.value
        acc
    }

    override fun close() {
        if (cacheManager.status != Status.UNINITIALIZED) {
            logger.info("Closing ehcache...")
            cacheManager.close()
        }
    }

    class CommandSerializer(cl: ClassLoader) : Serializer<Command> {
        constructor(cl: ClassLoader, fbpc: FileBasedPersistenceContext) : this(cl)

        override fun equals(obj: Command?, binary: ByteBuffer?): Boolean = obj == read(binary)

        override fun serialize(obj: Command?): ByteBuffer = ByteBuffer.wrap(CBOR.dump(obj!!))

        override fun read(binary: ByteBuffer?): Command = CBOR.load(binary!!.moveToByteArray())
    }
}
