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
import org.ehcache.spi.serialization.Serializer
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger


class LogDiskEhCache(filePath: String) : Log {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        private const val DISK_CACHE = "disk-log"
    }

    private val cacheManager: PersistentCacheManager
    private val diskStore: Cache<Int, Command>
    private val nextIndex: AtomicInteger = AtomicInteger(0)

    init {
        cacheManager = newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(File(filePath)))
                .withCache(DISK_CACHE,
                        newCacheConfigurationBuilder(
                                Int::class.javaObjectType,
                                Command::class.java,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .disk(10, MemoryUnit.MB, true)
                        ).withValueSerializer(CommandSerializer::class.java).build()
                )
                .build(true)

        diskStore = cacheManager.getCache(DISK_CACHE, Int::class.javaObjectType, Command::class.java)

        nextIndex.set(diskStore.count())

        Runtime.getRuntime().addShutdownHook(Thread { close() })
    }

    override fun append(cmd: Command) {
        nextIndex.getAndUpdate {
            diskStore.put(it, cmd)
            it + 1
        }
    }

    override fun toString() = diskStore.toList().fold("") { acc, cmd -> "$acc ${cmd.key} - ${cmd.value}, " }

    override fun commands(includePending: Boolean): List<Command> = diskStore.asSequence().sortedBy { it.key }.map { it.value }.toList()

    override fun lastIndex(): Int? = if (nextIndex.get() == 0) null else nextIndex.get() - 1

    override fun isEmpty(): Boolean = lastIndex() == null

    override fun get(i: Int): Command? = if (diskStore.containsKey(i)) diskStore[i] else null

    override fun last(): Command? = if (isEmpty()) null else get(lastIndex()!!)

    override fun close() {
        if (cacheManager.status != Status.UNINITIALIZED) {
            logger.info("Closing ehcache...")
            cacheManager.close()
        }
    }

    class CommandSerializer(cl: ClassLoader) : Serializer<Command> {
        //constructor(cl: ClassLoader, fbpc: FileBasedPersistenceContext) : this(cl)

        override fun equals(obj: Command?, binary: ByteBuffer?): Boolean = obj == read(binary)

        override fun serialize(obj: Command?): ByteBuffer = ByteBuffer.wrap(CBOR.dump(obj!!))

        override fun read(binary: ByteBuffer?): Command = CBOR.load(binary!!.moveToByteArray())
    }
}
