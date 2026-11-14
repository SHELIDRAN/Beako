package org.nekomanga.desktop.state

import java.awt.event.KeyEvent

/**
 * Reader actions that can be rebound. Keys are stored as AWT key codes. Esc always closes panels,
 * leaves full screen and then the reader, so it can't be rebound.
 */
enum class ReaderAction(val label: String, val defaults: List<Int>) {
    NEXT_PAGE(
        "Next page (or scroll down)",
        listOf(KeyEvent.VK_SPACE, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_DOWN),
    ),
    PREVIOUS_PAGE("Previous page (or scroll up)", listOf(KeyEvent.VK_PAGE_UP, KeyEvent.VK_UP)),
    RIGHT("Right: turn toward the right", listOf(KeyEvent.VK_RIGHT)),
    LEFT("Left: turn toward the left", listOf(KeyEvent.VK_LEFT)),
    NEXT_CHAPTER("Next chapter", listOf(KeyEvent.VK_CLOSE_BRACKET)),
    PREVIOUS_CHAPTER("Previous chapter", listOf(KeyEvent.VK_OPEN_BRACKET)),
    FULL_SCREEN("Full screen", listOf(KeyEvent.VK_F, KeyEvent.VK_F11)),
    SETTINGS("Reader settings", listOf(KeyEvent.VK_O)),
}

/** The reader's key map: saved overrides on top of the defaults. */
class KeyBindings(overrides: Map<String, List<Int>>) {
    /** The first key of [action], for labels such as "Next chapter (])". */
    fun hint(action: ReaderAction): String =
        map[action]?.firstOrNull()?.let { " (${name(it)})" }.orEmpty()

    val map: Map<ReaderAction, List<Int>> =
        ReaderAction.entries.associateWith { action -> overrides[action.name] ?: action.defaults }

    private val byKey: Map<Int, ReaderAction> =
        map.entries.flatMap { (action, keys) -> keys.map { it to action } }.toMap()

    fun actionFor(keyCode: Int): ReaderAction? =
        if (keyCode == KeyEvent.VK_ESCAPE) null else byKey[keyCode]

    companion object {
        /**
         * Overrides after binding [keyCode] to [action]: the key moves from any other action, so
         * one key never does two things.
         */
        fun bind(
            overrides: Map<String, List<Int>>,
            action: ReaderAction,
            keyCode: Int,
        ): Map<String, List<Int>> {
            require(keyCode != KeyEvent.VK_ESCAPE) { "Esc can’t be rebound." }
            val current = KeyBindings(overrides).map
            return current
                .mapValues { (other, keys) ->
                    if (other == action) (keys + keyCode).distinct() else keys - keyCode
                }
                .changedOnly()
        }

        fun unbind(
            overrides: Map<String, List<Int>>,
            action: ReaderAction,
            keyCode: Int,
        ): Map<String, List<Int>> =
            KeyBindings(overrides)
                .map
                .mapValues { (other, keys) -> if (other == action) keys - keyCode else keys }
                .mapKeys { it.key.name }

        /** "Space", "Page Down", "]". */
        fun name(keyCode: Int): String =
            when (keyCode) {
                KeyEvent.VK_OPEN_BRACKET -> "["
                KeyEvent.VK_CLOSE_BRACKET -> "]"
                else -> KeyEvent.getKeyText(keyCode)
            }

        /**
         * Only actions whose keys differ from the defaults are saved, so new defaults still apply.
         */
        private fun Map<ReaderAction, List<Int>>.changedOnly(): Map<String, List<Int>> =
            filter { (action, keys) ->
                keys != action.defaults
            }
            .mapKeys { it.key.name }
    }
}
