package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Frame
import org.nekomanga.desktop.platform.WindowsFullscreen
import org.nekomanga.desktop.platform.WindowsVault
import org.nekomanga.shared.diagnostics.DiagnosticLog

/** How full screen is applied; swapped in tests. */
interface FullscreenDriver {
    fun enter()

    fun exit()
}

/**
 * Full screen for the main window. On Windows it's borderless full screen ([WindowsFullscreen]),
 * since Compose's Fullscreen placement left the title bar on a decorated window; elsewhere it's
 * Compose's placement. [isFullscreen] follows what was actually applied, so the "Exit full screen"
 * label can't disagree with the window.
 */
class WindowControl(private val driver: FullscreenDriver) {
    private var applied by mutableStateOf(false)

    val isFullscreen: Boolean
        get() = applied

    fun setFullscreen(on: Boolean) {
        if (on == isFullscreen) return
        try {
            if (on) driver.enter() else driver.exit()
            applied = on
        } catch (e: Throwable) {
            // Never leave the window half-changed or the label lying: log and stay as we were.
            DiagnosticLog.error("Couldn’t ${if (on) "enter" else "leave"} full screen", e)
        }
    }

    fun toggleFullscreen() = setFullscreen(!isFullscreen)

    companion object {
        /** The driver for this system: borderless on Windows, Compose's placement elsewhere. */
        fun forWindow(state: WindowState, frame: () -> Frame?): WindowControl =
            WindowControl(
                if (WindowsVault.isWindows) WindowsDriver(frame) else PlacementDriver(state),
            )
    }
}

private class WindowsDriver(private val frame: () -> Frame?) : FullscreenDriver {
    private var saved: WindowsFullscreen.Saved? = null

    override fun enter() {
        val window = frame() ?: error("No window yet")
        saved = WindowsFullscreen.enter(window)
    }

    override fun exit() {
        val window = frame() ?: error("No window yet")
        saved?.let { WindowsFullscreen.exit(window, it) }
        saved = null
    }
}

private class PlacementDriver(private val state: WindowState) : FullscreenDriver {
    private var before = WindowPlacement.Floating

    override fun enter() {
        before = state.placement
        state.placement = WindowPlacement.Fullscreen
    }

    override fun exit() {
        state.placement = before
    }
}

/** Null in tests and previews, where there is no window. */
val LocalWindowControl = staticCompositionLocalOf<WindowControl?> { null }
