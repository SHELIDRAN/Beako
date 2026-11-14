package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nekomanga.shared.library.LibraryStore
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

/**
 * State of the reader: which chapter and page are open, and how pages are laid out. With a
 * [library], every page change is saved as progress and history, and reaching the last page marks
 * the chapter read.
 */
class ReaderModel(
    private val scope: CoroutineScope,
    private val mangaDex: MangaDexApi,
    val title: Title,
    /** Chapters in reading order (oldest first). */
    val chapters: List<Chapter>,
    startIndex: Int,
    private val library: LibraryStore? = null,
    /** Page to open the first chapter at, e.g. from saved progress. */
    startPage: Int = 0,
    /** When true, nothing is saved to progress or history. */
    private val incognito: () -> Boolean = { false },
    /** Downloaded page files of a chapter, read instead of the network when present. */
    private val localPages: (Chapter) -> List<java.io.File>? = { null },
    private val dataSaver: () -> Boolean = { false },
    /** Called once per chapter when it is finished, to sync trackers. */
    private val onChapterRead: (Chapter) -> Unit = {},
) {
    var index by mutableStateOf(startIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
        private set

    var pages by mutableStateOf<Loadable<PageList>>(Loadable.Loading)
        private set

    private val pageState = mutableStateOf(0)
    private var resumePage = startPage.coerceAtLeast(0)

    /** The page shown (paged modes) or at the top of the window (webtoon), from 0. */
    var page: Int
        get() = pageState.value
        set(value) {
            if (value == pageState.value) return
            pageState.value = value
            record()
        }

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

    /**
     * Saves the position. A chapter counts as read when the reader reaches its last page by
     * reading, not when it opens there (paging back into the previous chapter).
     */
    private fun record(markRead: Boolean = true) {
        val store = library ?: return
        val count = pageCount
        if (count == 0) return
        val current = page.coerceIn(0, count - 1)
        store.recordProgress(title, chapter, current, incognito())
        if (markRead && current == count - 1) markRead(chapter)
    }

    private fun markRead(done: Chapter) {
        val store = library ?: return
        if (!store.contains(title) || incognito()) return
        // Only a newly finished chapter syncs, so moving around the last page sends nothing more.
        if (done.id in store.entry(title)?.readChapterIds.orEmpty()) return
        store.setRead(title, listOf(done.id), true)
        onChapterRead(done)
    }

    private fun open(newIndex: Int, atEnd: Boolean) {
        // Moving on to the next chapter means this one is finished, even a one-page chapter.
        if (newIndex > index && pages is Loadable.Ready) markRead(chapter)
        index = newIndex
        openAtEnd = atEnd
        resumePage = 0
        pageState.value = 0
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
            try {
                val list =
                    localPages(target)?.let { files ->
                        PageList(files.map { LOCAL_PAGE + it.absolutePath })
                    } ?: mangaDex.pages(target.id, dataSaver())
                pages = Loadable.Ready(list)
                val last = (list.urls.size - 1).coerceAtLeast(0)
                val start =
                    when {
                        openAtEnd -> last
                        resumePage > 0 -> resumePage.coerceAtMost(last)
                        else -> 0
                    }
                resumePage = 0
                pageState.value = start
                record(markRead = false)
            } catch (e: ApiError) {
                pages = Loadable.Failed(e.message.orEmpty())
            }
        }
    }
}

/** Marks a page "URL" that is really a downloaded file's path. */
const val LOCAL_PAGE = "beako-file:"

/** The image to load for a page: a [java.io.File] for downloaded pages, else the URL. */
fun pageModel(url: String): Any =
    if (url.startsWith(LOCAL_PAGE)) java.io.File(url.removePrefix(LOCAL_PAGE)) else url

/** The highest chapter number among these, as written ("12.5"); null when none is numbered. */
fun List<Chapter>.highestNumber(): String? = mapNotNull { chapter ->
    chapter.number?.toDoubleOrNull()?.let { it to chapter.number }
}
    .maxByOrNull { it.first }
    ?.second

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
