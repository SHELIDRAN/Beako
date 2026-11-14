package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.nekomanga.desktop.state.KeyRouter

class KeyRouterTest {

    @Test
    fun keysGoToTheScreenThatClaimedThemAndNowhereOnceItLeaves() {
        val router = KeyRouter()
        val key =
            androidx.compose.ui.input.key.KeyEvent(
                java.awt.event.KeyEvent(
                    java.awt.Panel(),
                    java.awt.event.KeyEvent.KEY_PRESSED,
                    0,
                    0,
                    java.awt.event.KeyEvent.VK_RIGHT,
                    java.awt.event.KeyEvent.CHAR_UNDEFINED,
                ),
            )
        assertFalse(router.dispatch(key), "nothing claims keys outside the reader")
        var seen = 0
        router.handler = {
            seen++
            true
        }
        assertTrue(router.dispatch(key))
        router.handler = null
        assertFalse(router.dispatch(key))
        assertTrue(seen == 1)
    }
}
