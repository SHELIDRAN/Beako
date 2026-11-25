package org.nekomanga.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import org.nekomanga.desktop.state.WindowMemory
import org.nekomanga.shared.library.WindowBounds

class WindowMemoryTest {
    private val screen = listOf(Rectangle(0, 0, 1920, 1080))

    @Test
    fun aFirstStartIsMaximised() {
        assertEquals(WindowPlacement.Maximized, WindowMemory.placement(null))
        assertEquals(WindowMemory.DefaultSize, WindowMemory.size(null))
        assertEquals(WindowPosition.PlatformDefault, WindowMemory.position(null, screen))
    }

    @Test
    fun aSavedWindowOpensWhereItWas() {
        val saved = WindowBounds(200f, 120f, 1000f, 700f, maximized = false)
        assertEquals(WindowPlacement.Floating, WindowMemory.placement(saved))
        assertEquals(1000.dp, WindowMemory.size(saved).width)
        assertEquals(WindowPosition(200.dp, 120.dp), WindowMemory.position(saved, screen))
    }

    @Test
    fun aWindowLeftOnADisconnectedScreenComesBack() {
        val onSecondScreen = WindowBounds(2500f, 100f, 1000f, 700f, maximized = false)
        assertEquals(WindowPosition.PlatformDefault, WindowMemory.position(onSecondScreen, screen))
        // A tiny saved size falls back to the default.
        assertEquals(WindowMemory.DefaultSize, WindowMemory.size(WindowBounds(0f, 0f, 100f, 80f)))
    }
}
