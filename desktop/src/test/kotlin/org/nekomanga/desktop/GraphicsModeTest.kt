package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphicsModeTest {
    @Test
    fun theGraphicsSettingPicksSkiasRenderer() {
        assertNull(
            graphicsMode("AUTO"),
            "Automatic leaves the choice to Skia (Direct3D on Windows)",
        )
        assertEquals("OPENGL", graphicsMode("OPENGL"))
        assertEquals("SOFTWARE", graphicsMode("software"))
        assertNull(graphicsMode("nonsense"))
    }
}
