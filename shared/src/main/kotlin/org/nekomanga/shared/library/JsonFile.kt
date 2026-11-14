package org.nekomanga.shared.library

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Writes [text] to [file] through a temporary file and an atomic replace. */
fun writeAtomically(file: File, text: String) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, file.name + ".tmp")
    temp.writeText(text)
    try {
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (e: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Reads [file], retrying briefly when it is locked (antivirus and backup tools on Windows hold
 * files open for a moment). Returns null when the file doesn't exist.
 *
 * @throws IOException when it still can't be read.
 */
fun readWithRetry(file: File, attempts: Int = 5, waitMillis: Long = 200): String? {
    if (!file.exists()) return null
    var last: IOException? = null
    repeat(attempts) {
        try {
            return file.readText()
        } catch (e: IOException) {
            last = e
            Thread.sleep(waitMillis)
        }
    }
    throw last ?: IOException("Couldn't read ${file.name}")
}

/**
 * Saves a file in the background, at most once per [delayMillis]: many quick changes (such as page
 * turns) become one write, off the UI thread. [flush] writes any pending change straight away.
 */
class DebouncedWriter(private val file: File, private val delayMillis: Long = 400) {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "beako-save-${file.name}").apply { isDaemon = true }
    }
    private var pending: String? = null
    private var scheduled: ScheduledFuture<*>? = null

    /** The last failed write, if any. */
    @Volatile
    var lastError: IOException? = null
        private set

    @Synchronized
    fun write(text: String) {
        pending = text
        if (scheduled == null || scheduled!!.isDone) {
            scheduled = executor.schedule({ writePending() }, delayMillis, TimeUnit.MILLISECONDS)
        }
    }

    /** Writes any pending change now, on the calling thread. */
    fun flush() = writePending()

    private fun writePending() {
        val text = synchronized(this) { pending.also { pending = null } } ?: return
        try {
            writeAtomically(file, text)
            lastError = null
        } catch (e: IOException) {
            lastError = e
            // Keep the change so the next write or flush tries again.
            synchronized(this) { if (pending == null) pending = text }
        }
    }
}
