package org.nekomanga.desktop.state

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/**
 * Fullscreen control that always reads the real window state, so the "Exit full screen" label can
 * never disagree with the window.
 */
class WindowControl(private val window: WindowState) {
    private var before = WindowPlacement.Floating

    val isFullscreen: Boolean
        get() = window.placement == WindowPlacement.Fullscreen

    fun setFullscreen(on: Boolean) {
        if (on == isFullscreen) return
        if (on) {
            before = window.placement
            window.placement = WindowPlacement.Fullscreen
        } else {
            window.placement = before
        }
    }

    fun toggleFullscreen() = setFullscreen(!isFullscreen)
}

/** Null in tests and previews, where there is no window. */
val LocalWindowControl = staticCompositionLocalOf<WindowControl?> { null }
