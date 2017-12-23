package me.andresp.models

import com.squareup.tape2.ObjectQueue
import com.squareup.tape2.QueueFile
import kotlinx.serialization.cbor.CBOR
import java.io.File
import java.io.IOException
import java.io.OutputStream

class LogDisk(filePath: String) : Log {
    private val queue: ObjectQueue<Command>

    init {
        val queueFile = QueueFile.Builder(File(filePath)).build()
        queue = ObjectQueue.create(queueFile, CommandConverter())
    }

    override fun append(cmd: Command) {
        queue.add(cmd)
    }

    override fun printAll() {
        queue.asList().forEach { println(it) }
    }

    override fun commands(): List<Command> {
        return queue.asList()
    }

    private class CommandConverter : ObjectQueue.Converter<Command> {
        @Throws(IOException::class)
        override fun from(bytes: ByteArray): Command {
            return CBOR.load(bytes)
        }

        @Throws(IOException::class)
        override fun toStream(cmd: Command, os: OutputStream) {
            os.write(CBOR.Companion.dump(cmd))
        }
    }
}
