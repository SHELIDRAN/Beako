package org.nekomanga.desktop.state

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent

/**
 * Window-level keys for whichever screen claims them (the reader). Compose delivers keys only to
 * the focused element, and a clicked button that then disappears (the chapter's "Next chapter")
 * left the reader with no focus and dead arrow keys (0.1.2 QA). The window hands every key here
 * first.
 */
class KeyRouter {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) ?: false
}

/** Null in tests and previews; screens then handle keys through focus. */
val LocalKeyRouter = staticCompositionLocalOf<KeyRouter?> { null }
