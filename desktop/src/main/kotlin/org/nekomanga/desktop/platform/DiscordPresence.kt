package org.nekomanga.desktop.platform

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What Discord shows while reading. Null fields are left out. */
data class Activity(
    val details: String?,
    val state: String?,
    val startedAt: Long,
    val imageUrl: String?,
    val imageText: String?,
)

/** Writes and reads Discord IPC frames: little-endian opcode and length, then JSON. */
interface DiscordPipe : java.io.Closeable {
    fun write(opcode: Int, json: String)

    fun read(): Pair<Int, String>
}

/**
 * Discord Rich Presence over Discord's local IPC pipe (`\\.\pipe\discord-ipc-N` on Windows). Talks
 * to the Discord app on this PC only; nothing goes to the internet from Beako. All calls run on one
 * background thread, and failures (Discord closed) are ignored until the next update.
 */
class DiscordPresence(
    private val clientId: () -> String,
    private val connect: () -> DiscordPipe? = ::openWindowsPipe,
) : java.io.Closeable {
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "beako-discord").apply { isDaemon = true }
    }
    private var pipe: DiscordPipe? = null
    private var connectedId: String? = null

    /** Shows [activity], or clears the presence when null. */
    fun update(activity: Activity?) {
        worker.execute {
            try {
                send(activity)
            } catch (e: IOException) {
                drop()
            }
        }
    }

    private fun send(activity: Activity?) {
        val id = clientId().trim()
        if (id.isEmpty() || !id.all(Char::isDigit)) {
            drop()
            return
        }
        val open = pipe?.takeIf { connectedId == id } ?: handshake(id) ?: return
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("nonce", UUID.randomUUID().toString())
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                if (activity != null) put("activity", activityJson(activity))
            }
        }
        open.write(OP_FRAME, payload.toString())
        open.read()
    }

    private fun handshake(id: String): DiscordPipe? {
        drop()
        val opened = connect() ?: return null
        opened.write(
            OP_HANDSHAKE,
            buildJsonObject {
                put("v", 1)
                put("client_id", id)
            }
                .toString(),
        )
        val (op, _) = opened.read()
        if (op != OP_FRAME) {
            opened.close()
            return null
        }
        pipe = opened
        connectedId = id
        return opened
    }

    private fun drop() {
        runCatching { pipe?.close() }
        pipe = null
        connectedId = null
    }

    override fun close() {
        worker.execute { drop() }
        worker.shutdown()
    }

    companion object {
        const val OP_HANDSHAKE = 0
        const val OP_FRAME = 1

        fun activityJson(activity: Activity): JsonObject = buildJsonObject {
            activity.details?.let { put("details", it.take(128)) }
            activity.state?.let { put("state", it.take(128)) }
            putJsonObject("timestamps") { put("start", activity.startedAt / 1000) }
            putJsonObject("assets") {
                // Discord shows https images by URL; otherwise the app's uploaded "beako" icon.
                put(
                    "large_image",
                    activity.imageUrl?.takeIf { it.startsWith("https://") } ?: "beako",
                )
                put("large_text", (activity.imageText ?: "Beako").take(128))
            }
            put("instance", false)
        }

        fun frame(opcode: Int, json: String): ByteArray {
            val body = json.toByteArray(Charsets.UTF_8)
            return ByteBuffer.allocate(8 + body.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode)
                .putInt(body.size)
                .put(body)
                .array()
        }

        /** The first Discord pipe that opens, or null when Discord isn't running. */
        fun openWindowsPipe(): DiscordPipe? {
            if (!WindowsVault.isWindows) return null
            for (n in 0..9) {
                val file =
                    runCatching { RandomAccessFile(File("\\\\.\\pipe\\discord-ipc-$n"), "rw") }
                        .getOrNull() ?: continue
                return object : DiscordPipe {
                    override fun write(opcode: Int, json: String) = file.write(frame(opcode, json))

                    override fun read(): Pair<Int, String> {
                        val header = ByteArray(8).also(file::readFully)
                        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        val op = buffer.int
                        val length = buffer.int
                        if (length !in 0..1_000_000) throw IOException("Bad frame from Discord")
                        return op to String(ByteArray(length).also(file::readFully), Charsets.UTF_8)
                    }

                    override fun close() = file.close()
                }
            }
            return null
        }
    }
}
