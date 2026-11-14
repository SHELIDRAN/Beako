package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.PageList
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

enum class ReadingMode(val label: String) {
    WEBTOON("Webtoon"),
    LEFT_TO_RIGHT("Left to right"),
    RIGHT_TO_LEFT("Right to left");

    val isPaged: Boolean
        get() = this != WEBTOON

    companion object {
        /** Long-strip for manhwa and manhua, right-to-left pages for manga. */
        fun defaultFor(type: ComicType?): ReadingMode =
            when (type) {
                ComicType.MANHWA,
                ComicType.MANHUA -> WEBTOON
                ComicType.MANGA -> RIGHT_TO_LEFT
                else -> LEFT_TO_RIGHT
            }
    }
}

/** State of the reader: which chapter and page are open, and how pages are laid out. */
class ReaderModel(
    private val scope: CoroutineScope,
    private val mangaDex: MangaDexApi,
    val title: Title,
    /** Chapters in reading order (oldest first). */
    val chapters: List<Chapter>,
    startIndex: Int,
) {
    var index by mutableStateOf(startIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
        private set

    var pages by mutableStateOf<Loadable<PageList>>(Loadable.Loading)
        private set

    /** The page shown (paged modes) or at the top of the window (webtoon), from 0. */
    var page by mutableStateOf(0)

    var mode by mutableStateOf(ReadingMode.defaultFor(title.type))

    /** Set when a chapter opens at its last page, after paging back from the next chapter. */
    var openAtEnd by mutableStateOf(false)
        private set

    val chapter: Chapter
        get() = chapters[index]

    val pageCount: Int
        get() = (pages as? Loadable.Ready)?.value?.urls?.size ?: 0

    val hasNext: Boolean
        get() = index < chapters.lastIndex

    val hasPrevious: Boolean
        get() = index > 0

    private var job: Job? = null
    private var started = false

    fun start() {
        if (!started) {
            started = true
            load()
        }
    }

    fun retry() = load()

    fun nextChapter() {
        if (hasNext) open(index + 1, atEnd = false)
    }

    fun previousChapter(atEnd: Boolean = false) {
        if (hasPrevious) open(index - 1, atEnd)
    }

    /**
     * Turns one page in the paged modes. Past the last page it opens the next chapter; before the
     * first it opens the previous chapter at its last page.
     */
    fun turn(forward: Boolean) {
        if (pages !is Loadable.Ready) return
        when {
            forward && page < pageCount - 1 -> page += 1
            forward -> nextChapter()
            page > 0 -> page -= 1
            else -> previousChapter(atEnd = true)
        }
    }

    private fun open(newIndex: Int, atEnd: Boolean) {
        index = newIndex
        openAtEnd = atEnd
        page = 0
        load()
    }

    private fun load() {
        job?.cancel()
        pages = Loadable.Loading
        val target = chapter
        if (target.externalUrl != null) {
            // Hosted by the publisher: the screen offers to open it in the browser.
            pages = Loadable.Ready(PageList(emptyList()))
            return
        }
        job = scope.launch {
            pages =
                try {
                    val list = mangaDex.pages(target.id)
                    if (openAtEnd) page = (list.urls.size - 1).coerceAtLeast(0)
                    Loadable.Ready(list)
                } catch (e: ApiError) {
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }
}

/** "Chapter 12", "Vol. 2 Chapter 12 · Title", or the title alone for unnumbered chapters. */
fun Chapter.displayName(): String {
    val number = number?.let { "Chapter $it" }
    val head = listOfNotNull(volume?.let { "Vol. $it" }, number).joinToString(" ")
    return when {
        head.isNotEmpty() && title != null -> "$head · $title"
        head.isNotEmpty() -> head
        else -> title ?: "Oneshot"
    }
}
