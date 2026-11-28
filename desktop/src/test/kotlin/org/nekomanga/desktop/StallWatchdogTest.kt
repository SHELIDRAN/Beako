package org.nekomanga.desktop

import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.nekomanga.desktop.platform.StallWatchdog

class StallWatchdogTest {

    private val ui = Executors.newSingleThreadExecutor { Thread(it, "fake-ui") }

    private fun uiThread(): Thread {
        var thread: Thread? = null
        ui.submit { thread = Thread.currentThread() }.get()
        return thread!!
    }

    @Test
    fun aBlockedUiThreadIsReportedWithWhatItWasDoing() {
        val reports = mutableListOf<String>()
        val thread = uiThread()
        val watchdog =
            StallWatchdog(
                post = { ui.execute(it) },
                uiThread = { thread },
                screen = { "BROWSE, menu opening" },
                report = { reports += it },
                thresholdMillis = 20,
            )
        ui.execute { slowWork() }
        watchdog.checkOnce()

        assertEquals(1, reports.size)
        val report = reports.single()
        assertTrue(report.startsWith("UI stalled "), report)
        assertTrue("on BROWSE, menu opening" in report, report)
        assertTrue("slowWork" in report, "the stack names the blocking code: $report")
    }

    @Test
    fun aResponsiveUiThreadIsNotReported() {
        val reports = mutableListOf<String>()
        val watchdog =
            StallWatchdog(
                post = { ui.execute(it) },
                uiThread = { null },
                screen = { "LIBRARY" },
                report = { reports += it },
                thresholdMillis = 200,
            )
        repeat(3) { watchdog.checkOnce() }
        assertEquals(emptyList(), reports)
    }

    @Test
    fun reportsAreSpacedOut() {
        val reports = mutableListOf<String>()
        val watchdog =
            StallWatchdog(
                post = { ui.execute(it) },
                uiThread = { null },
                screen = { "MORE" },
                report = { reports += it },
                thresholdMillis = 10,
                quietMillis = 60_000,
            )
        repeat(2) {
            ui.execute { Thread.sleep(80) }
            watchdog.checkOnce()
        }
        assertEquals(1, reports.size)
    }

    private fun slowWork() {
        Thread.sleep(300)
    }
}
