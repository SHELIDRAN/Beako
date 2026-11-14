package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
    /** The mode last chosen for this title, if any. */
    savedMode: ReadingMode? = null,
    /** Remembers the mode the user picks for this title. */
    private val saveMode: (ReadingMode) -> Unit = {},
    private val autoWebtoon: () -> Boolean = { true },
    private val splitWidePages: () -> Boolean = { false },
    /** Pages from wherever the chapter lives; MangaDex when not given. */
    private val pageSource: (suspend (Chapter) -> PageList)? = null,
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

    private val modeState = mutableStateOf(savedMode ?: ReadingMode.defaultFor(title.type))
    private var modeChosen = savedMode != null

    /** The layout. Setting it is the user's choice: it is remembered and stops auto-detection. */
    var mode: ReadingMode
        get() = modeState.value
        set(value) {
            modeChosen = true
            halfState.value = 0
            if (value != modeState.value) {
                modeState.value = value
                saveMode(value)
            }
        }

    /** True when the reader switched to webtoon by itself, for a one-time notice. */
    var autoSwitched by mutableStateOf(false)
        private set

    private val halfState = mutableStateOf(0)
    private var endHalfPending = false

    /** With split wide pages: 0 shows the half read first, 1 the other half. */
    val half: Int
        get() = halfState.value

    /** Height÷width of pages loaded in the current chapter, by index. */
    private val aspects = mutableStateMapOf<Int, Float>()

    /** The loaded page's height÷width, or a portrait default. */
    fun aspectOf(index: Int): Float = aspects[index] ?: 1.45f

    /** True when [index] is a double-page spread to show in halves. */
    fun isSplit(index: Int): Boolean =
        mode.isPaged && splitWidePages() && (aspects[index] ?: return false) < WIDE_ASPECT

    /**
     * Reports a loaded page's height÷width. Long-strip pages switch a title to webtoon mode unless
     * the user picked a mode for it.
     */
    fun reportAspect(index: Int, aspect: Float) {
        if (aspect <= 0f) return
        aspects[index] = aspect
        // Paging back into a chapter that ends on a spread lands on the spread's second half.
        if (endHalfPending && index == page && isSplit(index)) {
            endHalfPending = false
            halfState.value = 1
        }
        if (modeChosen || mode == ReadingMode.WEBTOON || !autoWebtoon()) return
        if (aspects.values.count { it >= STRIP_ASPECT } >= 2) {
            modeState.value = ReadingMode.WEBTOON
            autoSwitched = true
        }
    }

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
            forward && isSplit(page) && half == 0 -> {
                halfState.value = 1
                record()
            }
            !forward && isSplit(page) && half == 1 -> halfState.value = 0
            forward && page < pageCount - 1 -> {
                halfState.value = 0
                page += 1
            }
            forward -> nextChapter()
            page > 0 -> {
                page -= 1
                // Coming back into a spread starts on its second half.
                halfState.value = if (isSplit(page)) 1 else 0
            }
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
        // A last-page spread counts once its second half is shown.
        val finished = current == count - 1 && !(isSplit(current) && half == 0)
        if (markRead && finished) markRead(chapter)
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
        halfState.value = 0
        aspects.clear()
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
                    } ?: pageSource?.invoke(target) ?: mangaDex.pages(target.id, dataSaver())
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
                endHalfPending = openAtEnd
                record(markRead = false)
            } catch (e: ApiError) {
                pages = Loadable.Failed(e.message.orEmpty())
            }
        }
    }
}

/** Pages taller than this (height÷width) are long strips. */
private const val STRIP_ASPECT = 2.2f

/** Pages wider than tall by this much are double-page spreads. */
private const val WIDE_ASPECT = 0.85f

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
