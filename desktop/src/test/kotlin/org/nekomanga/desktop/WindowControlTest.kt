package org.nekomanga.desktop

import java.awt.Frame
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.nekomanga.desktop.platform.WindowsFullscreen
import org.nekomanga.desktop.platform.WindowsVault
import org.nekomanga.desktop.state.FullscreenDriver
import org.nekomanga.desktop.state.WindowControl

class WindowControlTest {

    @Test
    fun theLabelFollowsWhatWasAppliedEvenWhenItFails() {
        var fail = false
        val calls = mutableListOf<String>()
        val control =
            WindowControl(
                object : FullscreenDriver {
                    override fun enter() {
                        if (fail) error("no window")
                        calls += "enter"
                    }

                    override fun exit() {
                        calls += "exit"
                    }
                },
            )
        control.toggleFullscreen()
        assertTrue(control.isFullscreen)
        control.setFullscreen(true)
        control.toggleFullscreen()
        assertFalse(control.isFullscreen)
        assertEquals(listOf("enter", "exit"), calls)

        fail = true
        control.setFullscreen(true)
        assertFalse(control.isFullscreen, "a failed switch leaves the label as it was")
    }

    /** Windows only (CI): a real window loses its title bar, covers the monitor, and comes back. */
    @Test
    fun borderlessFullScreenRemovesTheTitleBarAndRestoresIt() {
        assumeTrue(WindowsVault.isWindows && !GraphicsEnvironment.isHeadless())
        val frame =
            Frame("Beako full screen test").apply {
                setBounds(100, 100, 800, 600)
                isVisible = true
            }
        try {
            Thread.sleep(300)
            val before = frame.bounds
            assertTrue(frame.insets.top > 0, "a normal window has a title bar")

            val saved = WindowsFullscreen.enter(frame)
            Thread.sleep(300)
            val screen = frame.graphicsConfiguration.bounds
            assertEquals(0, frame.insets.top, "no title bar in full screen")
            assertTrue(
                frame.width >= screen.width && frame.height >= screen.height,
                "${frame.bounds} vs $screen",
            )

            WindowsFullscreen.exit(frame, saved)
            Thread.sleep(300)
            assertTrue(frame.insets.top > 0, "the title bar is back")
            assertEquals(before, frame.bounds)
        } finally {
            frame.dispose()
        }
    }
}
