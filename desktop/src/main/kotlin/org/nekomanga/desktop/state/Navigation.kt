package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.nekomanga.desktop.components.Destination
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title

/** Something that is loading, ready, or failed with a message the user can read. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Ready<T>(val value: T) : Loadable<T>

    data class Failed(val message: String) : Loadable<Nothing>
}

/** A page opened on top of a sidebar screen. */
sealed interface Page {
    data class Search(val query: String = "") : Page

    data class Details(val title: Title) : Page

    /** [chapters] are in reading order; [index] is the one to open. */
    data class Reader(
        val title: Title,
        val chapters: List<Chapter>,
        val index: Int,
        val page: Int = 0,
    ) : Page
}

class Navigator {
    private val stack = mutableStateListOf<Page>()

    val top: Page?
        get() = stack.lastOrNull()

    fun open(page: Page) {
        stack += page
    }

    /** Closes the top page. Returns false when there was none. */
    fun back(): Boolean = stack.removeLastOrNull() != null

    fun clear() = stack.clear()

    /** A sidebar screen a page asked to switch to; the shell applies it and clears it. */
    var requestedDestination by mutableStateOf<Destination?>(null)

    /** Closes every page and shows [destination]. */
    fun show(destination: Destination) {
        stack.clear()
        requestedDestination = destination
    }
}
