package org.nekomanga.desktop.platform

import org.nekomanga.shared.diagnostics.DiagnosticLog

/**
 * Logs when the UI thread is held up long enough to drop frames, with what it was doing, so a
 * stutter report comes with evidence in `beako.log` (0.1.12: menus stuttered on the owner's PC with
 * the glass blur and the graphics mode both ruled out).
 *
 * Every [periodMillis] a background thread posts an empty task to the UI thread. If it hasn't run
 * after [thresholdMillis], the UI thread's stack is captured then (the code that's holding it up),
 * and once it runs the stall is reported with its length and what was on screen. Reports are capped
 * at [maxReports] per run and spaced [quietMillis] apart, so the log stays small.
 */
class StallWatchdog(
    private val post: (Runnable) -> Unit,
    private val uiThread: () -> Thread?,
    private val screen: () -> String,
    private val report: (String) -> Unit,
    private val thresholdMillis: Long = 50,
    private val periodMillis: Long = 100,
    private val quietMillis: Long = 2_000,
    private val maxReports: Int = 60,
) {
    @Volatile private var running = false
    private var reports = 0
    private var lastReportAt = Long.MIN_VALUE / 2

    fun start() {
        if (running) return
        running = true
        Thread({ loop() }, "beako-stall-watchdog").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
    }

    private fun loop() {
        while (running && reports < maxReports) {
            runCatching { checkOnce() }
            Thread.sleep(periodMillis)
        }
    }

    /** One probe of the UI thread; internal so tests can drive it without the timing loop. */
    internal fun checkOnce() {
        val done = java.util.concurrent.CountDownLatch(1)
        val posted = System.nanoTime()
        post(Runnable { done.countDown() })
        if (done.await(thresholdMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) return
        // Still blocked: what is it doing right now?
        val stack = uiThread()?.stackTrace.orEmpty()
        val onScreen = screen()
        done.await()
        val stalledMillis = (System.nanoTime() - posted) / 1_000_000
        val nowMillis = System.nanoTime() / 1_000_000
        if (nowMillis - lastReportAt < quietMillis) return
        lastReportAt = nowMillis
        reports++
        report(
            buildString {
                append("UI stalled $stalledMillis ms on $onScreen")
                if (stack.isEmpty()) append(" (no stack captured)")
                for (frame in stack.take(STACK_FRAMES)) append("\n\tat ").append(frame)
            },
        )
    }

    companion object {
        private const val STACK_FRAMES = 25

        /** The running app's watchdog: posts to the Swing event thread and writes to beako.log. */
        fun forApp(): StallWatchdog {
            val edt = java.util.concurrent.atomic.AtomicReference<Thread?>()
            javax.swing.SwingUtilities.invokeLater { edt.set(Thread.currentThread()) }
            return StallWatchdog(
                post = { javax.swing.SwingUtilities.invokeLater(it) },
                uiThread = { edt.get() },
                screen = { "${OnScreen.page}, menu ${OnScreen.menu}" },
                report = { DiagnosticLog.info(it) },
            )
        }
    }
}

/** What the UI shows, for [StallWatchdog]'s reports. Set from composition. */
object OnScreen {
    @Volatile var page: String = "startup"
    @Volatile var menu: String = "closed"
}
