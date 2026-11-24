package org.nekomanga.desktop.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Ctrl+F: the search box of the screen on show registers here, and the shortcut focuses the most
 * recently shown one. With none registered (e.g. Browse, whose box opens Search), the shell opens
 * Search instead.
 */
class SearchShortcut {
    private val fields = mutableListOf<FocusRequester>()

    fun register(field: FocusRequester) {
        fields += field
    }

    fun unregister(field: FocusRequester) {
        fields -= field
    }

    /** Focuses the current screen's search box; false when there is none. */
    fun focus(): Boolean {
        val field = fields.lastOrNull() ?: return false
        return runCatching { field.requestFocus() }.isSuccess
    }
}

val LocalSearchShortcut = staticCompositionLocalOf { SearchShortcut() }

/** A focus requester for this screen's search box, which Ctrl+F focuses while it's shown. */
@Composable
fun rememberSearchShortcut(): FocusRequester {
    val shortcut = LocalSearchShortcut.current
    val field = remember { FocusRequester() }
    DisposableEffect(shortcut, field) {
        shortcut.register(field)
        onDispose { shortcut.unregister(field) }
    }
    return field
}
