package org.nekomanga.shared.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * A plain-text log for debugging installed builds: `beako.log` in Beako's data folder
 * (`%APPDATA%\Beako` on Windows). Holds source failures, image load failures and crashes, never
 * tokens or passwords. Kept under [maxBytes]; the previous file is kept as `beako.log.1`.
 */
object DiagnosticLog {
    @Volatile private var file: File? = null
    private var maxBytes = 1_000_000L

    /** Starts logging to [folder]/beako.log. Until then, messages are dropped. */
    fun start(folder: File, maxBytes: Long = 1_000_000L) {
        folder.mkdirs()
        this.maxBytes = maxBytes
        file = File(folder, "beako.log")
    }

    val path: File?
        get() = file

    fun info(message: String) = write("INFO", message, null)

    fun error(message: String, error: Throwable? = null) = write("ERROR", message, error)

    @Synchronized
    private fun write(level: String, message: String, error: Throwable?) {
        val target = file ?: return
        try {
            if (target.length() > maxBytes) {
                val old = File(target.parentFile, target.name + ".1")
                old.delete()
                target.renameTo(old)
            }
            val stack = error?.let { e ->
                StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
            }
            val line =
                "${OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)} $level $message\n"
            target.appendText(line + stack.orEmpty())
        } catch (e: Exception) {
            // Logging must never break the app.
        }
    }
}
