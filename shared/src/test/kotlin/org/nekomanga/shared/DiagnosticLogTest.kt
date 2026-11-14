package org.nekomanga.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import org.nekomanga.shared.diagnostics.DiagnosticLog

class DiagnosticLogTest {

    @Test
    fun errorsAreWrittenWithTheirStackAndTheFileIsKeptSmall() {
        val dir = Files.createTempDirectory("beako-log").toFile()
        DiagnosticLog.start(dir, maxBytes = 2_000)
        DiagnosticLog.error("Source Sample (id 1) failed", IllegalStateException("site changed"))
        val log = File(dir, "beako.log").readText()
        assertTrue(log.contains("ERROR Source Sample (id 1) failed"))
        assertTrue(log.contains("java.lang.IllegalStateException: site changed"))
        assertTrue(log.contains("\tat "), "the stack trace is included")

        repeat(50) { DiagnosticLog.info("line $it with some padding to grow the file quickly") }
        assertTrue(File(dir, "beako.log.1").exists(), "the old log is rotated out")
        assertTrue(File(dir, "beako.log").length() < 4_000)
    }
}
