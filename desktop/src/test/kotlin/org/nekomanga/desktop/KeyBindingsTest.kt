package org.nekomanga.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.nekomanga.desktop.state.KeyBindings
import org.nekomanga.desktop.state.ReaderAction

class KeyBindingsTest {

    @Test
    fun defaultsMatchComposeKeysAndEscIsNeverBound() {
        val keys = KeyBindings(emptyMap())
        assertEquals(ReaderAction.NEXT_PAGE, keys.actionFor(Key.Spacebar.nativeKeyCode))
        assertEquals(ReaderAction.NEXT_CHAPTER, keys.actionFor(Key.RightBracket.nativeKeyCode))
        assertEquals(ReaderAction.FULL_SCREEN, keys.actionFor(Key.F11.nativeKeyCode))
        assertNull(keys.actionFor(Key.Escape.nativeKeyCode))
    }

    @Test
    fun bindingAKeyMovesItFromItsOldAction() {
        val overrides = KeyBindings.bind(emptyMap(), ReaderAction.NEXT_CHAPTER, KeyEvent.VK_SPACE)
        // Only the two changed actions are saved.
        assertEquals(setOf("NEXT_CHAPTER", "NEXT_PAGE"), overrides.keys)
        val keys = KeyBindings(overrides)
        assertEquals(ReaderAction.NEXT_CHAPTER, keys.actionFor(KeyEvent.VK_SPACE))
        assertEquals(
            listOf(KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_DOWN),
            keys.map[ReaderAction.NEXT_PAGE],
        )

        val removed = KeyBindings.unbind(overrides, ReaderAction.NEXT_CHAPTER, KeyEvent.VK_SPACE)
        assertNull(KeyBindings(removed).actionFor(KeyEvent.VK_SPACE))
        assertFailsWith<IllegalArgumentException> {
            KeyBindings.bind(emptyMap(), ReaderAction.LEFT, KeyEvent.VK_ESCAPE)
        }
        assertEquals("Space", KeyBindings.name(KeyEvent.VK_SPACE))
        assertEquals(" (])", KeyBindings(emptyMap()).hint(ReaderAction.NEXT_CHAPTER))
    }
}
