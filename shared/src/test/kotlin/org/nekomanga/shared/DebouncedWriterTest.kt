package org.nekomanga.shared

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.nekomanga.shared.library.DebouncedWriter

class DebouncedWriterTest {
    @Test
    fun manyQuickChangesEncodeOnceOffTheCallingThread() {
        val file = Files.createTempFile("beako", ".json").toFile()
        val writer = DebouncedWriter(file, delayMillis = 100)
        val encodes = AtomicInteger()
        var encodedOn: Thread? = null
        val caller = Thread.currentThread()
        repeat(50) { i ->
            writer.writeLater {
                encodes.incrementAndGet()
                encodedOn = Thread.currentThread()
                "{\"n\":$i}"
            }
        }
        Thread.sleep(600)
        // Only the last change is encoded, once, on the save thread.
        assertEquals(1, encodes.get())
        assertNotEquals(caller, encodedOn)
        assertEquals("{\"n\":49}", file.readText())
        // flush writes a pending change straight away.
        writer.writeLater { "{\"n\":50}" }
        writer.flush()
        assertEquals("{\"n\":50}", file.readText())
    }
}
