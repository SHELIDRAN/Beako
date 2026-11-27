package org.nekomanga.desktop.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.nekomanga.shared.library.WindowBounds

/** Where the window opens: the saved place when it's still on a screen, else centred, maximised. */
object WindowMemory {
    val DefaultSize = DpSize(1280.dp, 800.dp)

    fun placement(saved: WindowBounds?): WindowPlacement =
        if (saved == null || saved.maximized) WindowPlacement.Maximized
        else WindowPlacement.Floating

    fun size(saved: WindowBounds?): DpSize =
        saved
            ?.takeIf { it.width >= MIN_WIDTH && it.height >= MIN_HEIGHT }
            ?.let { DpSize(it.width.dp, it.height.dp) } ?: DefaultSize

    fun position(saved: WindowBounds?, screens: List<Rectangle> = screens()): WindowPosition {
        saved ?: return WindowPosition.PlatformDefault
        // At least a strip of the title bar must be on a screen that's still connected.
        val titleBar =
            Rectangle(
                saved.x.toInt(),
                saved.y.toInt(),
                saved.width.toInt().coerceAtLeast(1),
                TITLE_BAR,
            )
        return if (screens.any { it.intersects(titleBar) }) WindowPosition(saved.x.dp, saved.y.dp)
        else WindowPosition.PlatformDefault
    }

    private fun screens(): List<Rectangle> = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map {
            it.defaultConfiguration.bounds
        }
    }
        .getOrDefault(emptyList())

    private const val MIN_WIDTH = 640f
    private const val MIN_HEIGHT = 420f
    private const val TITLE_BAR = 32
}

/**
 * Saves the window's normal bounds and whether it's maximised, a moment after it stops changing.
 * Full screen counts as maximised (Beako is maximised underneath it).
 */
@Composable
fun RememberWindowBounds(
    state: WindowState,
    fullscreen: () -> Boolean,
    saved: () -> WindowBounds?,
    save: (WindowBounds) -> Unit,
) {
    LaunchedEffect(state) {
        snapshotFlow {
            Triple(state.placement, state.position, state.size) to
                (fullscreen() to state.isMinimized)
        }
            .distinctUntilChanged()
            .collectLatest { (window, flags) ->
                val (isFullscreen, minimized) = flags
                // Minimised, Windows parks the window off-screen at a tiny size: keep what was
                // saved before.
                if (minimized) return@collectLatest
                delay(500)
                val (placement, position, size) = window
                val floating = placement == WindowPlacement.Floating && !isFullscreen
                val previous = saved()
                val next =
                    if (floating && position is WindowPosition.Absolute)
                        WindowBounds(
                            position.x.value,
                            position.y.value,
                            size.width.value,
                            size.height.value,
                            maximized = false,
                        )
                    else if (placement == WindowPlacement.Maximized || isFullscreen)
                        (previous
                                ?: WindowBounds(
                                    0f,
                                    0f,
                                    WindowMemory.DefaultSize.width.value,
                                    WindowMemory.DefaultSize.height.value,
                                ))
                            .copy(maximized = true)
                    else return@collectLatest
                if (next != previous) save(next)
            }
    }
}
