package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.download.DownloadManager
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/**
 * State of a title page. The title can come from Comick or MangaDex; its chapters come from the
 * reading sources that carry it. MangaDex is the only reading source until extensions land.
 */
class DetailsModel(
    private val scope: CoroutineScope,
    private val discovery: Discovery,
    private val mangaDex: MangaDexApi,
    private val library: LibraryStore,
    val downloads: DownloadManager,
    val title: Title,
    /** Chapter languages to list, as MangaDex codes. */
    private val languages: () -> List<String> = { listOf("en") },
    /** Called when chapters are marked read here, to sync trackers. */
    private val onChaptersRead: (List<Chapter>) -> Unit = {},
) {
    /** Chapters newest first, as the list shows them. */
    var chapters by mutableStateOf<Loadable<List<Chapter>>>(Loadable.Loading)
        private set

    /** Names of the reading sources that have this title; empty until the lookup finishes. */
    var availableOn by mutableStateOf<List<String>>(emptyList())
        private set

    private var mangaDexId: String? = library.entry(title)?.mangaDexId

    /** Scanlation groups hidden on this page and in the reader. Saved for library titles. */
    var excludedGroups by mutableStateOf(library.entry(title)?.excludedGroups.orEmpty())
        private set

    /** Popular titles sharing this one's genres; null until loaded. */
    var similar by mutableStateOf<Loadable<List<Title>>>(Loadable.Loading)
        private set

    /** Every group that has chapters here, most chapters first. */
    fun groups(): List<String> =
        allChapters()
            .mapNotNull { it.group }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

    fun setGroupHidden(group: String, hidden: Boolean) {
        excludedGroups = if (hidden) excludedGroups + group else excludedGroups - group
        if (library.contains(title)) library.setExcludedGroups(title, excludedGroups)
    }

    /**
     * Chapters from groups that aren't hidden, newest first. Chapters with no group always show.
     */
    fun visibleChapters(): List<Chapter> =
        allChapters().filter { it.group == null || it.group !in excludedGroups }

    private fun allChapters(): List<Chapter> = (chapters as? Loadable.Ready)?.value.orEmpty()

    private var started = false

    fun start() {
        if (!started) {
            started = true
            load()
        }
    }

    fun retry() = load()

    /** The library's contents, for the page to follow changes as they happen. */
    val libraryData = library.data

    /** The library entry, read fresh so the page follows changes made elsewhere. */
    fun entry(): LibraryEntry? = library.entry(title)

    val categories: List<String>
        get() = library.data.value.categories

    fun addToLibrary(categories: List<String> = emptyList()) {
        library.add(title, categories, mangaDexId)
        if (excludedGroups.isNotEmpty()) library.setExcludedGroups(title, excludedGroups)
        // Record what exists now, so the first update check doesn't report every chapter as new.
        (chapters as? Loadable.Ready)?.value?.let {
            library.recordCheck(LibraryEntry.keyOf(title), it)
        }
    }

    fun removeFromLibrary() = library.remove(title)

    fun setCategories(categories: List<String>) = library.setCategories(title, categories)

    fun addCategory(name: String) = library.addCategory(name)

    fun setRead(chapters: Collection<Chapter>, read: Boolean) {
        if (!library.contains(title)) return
        library.setRead(title, chapters.map { it.id }, read)
        if (read) onChaptersRead(chapters.toList())
    }

    fun download(chapters: List<Chapter>) = downloads.enqueue(title, chapters)

    fun deleteDownload(chapter: Chapter) = downloads.delete(title, chapter)

    fun isDownloaded(chapter: Chapter) = downloads.isDownloaded(title, chapter)

    /** Unread chapters in reading order, for "Download unread". */
    fun unreadChapters(): List<Chapter> {
        val read = entry()?.readChapterIds.orEmpty()
        return readingOrder().filter { it.id !in read && it.externalUrl == null }
    }

    /** Marks this chapter and every earlier one as read. */
    fun markReadUpTo(chapter: Chapter) {
        val order = readingOrder()
        val upTo = order.indexOfFirst { it.id == chapter.id }
        if (upTo >= 0) setRead(order.subList(0, upTo + 1), true)
    }

    /** The highest chapter number read, for a tracker's progress; null when none is numbered. */
    fun readProgress(): String? {
        val read = entry()?.readChapterIds.orEmpty()
        return readingOrder().filter { it.id in read }.highestNumber()
    }

    /** [chapters] oldest first, the order the reader moves through them. */
    fun readingOrder(): List<Chapter> = visibleChapters().reversed()

    /** Where "Continue" should open: the saved chapter, else the first unread one. */
    fun continueIndex(): Int? {
        val order =
            readingOrder().ifEmpty {
                return null
            }
        val entry = entry() ?: return null
        entry.progress?.let { progress ->
            val saved = order.indexOfFirst { it.id == progress.chapterId }
            if (saved >= 0) return saved
        }
        val unread = order.indexOfFirst { it.id !in entry.readChapterIds }
        return if (unread >= 0) unread else null
    }

    private fun loadSimilar() {
        similar = Loadable.Loading
        scope.launch {
            similar =
                try {
                    Loadable.Ready(discovery.similar(title))
                } catch (e: ApiError) {
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }

    private fun load() {
        if (similar !is Loadable.Ready) loadSimilar()
        chapters = Loadable.Loading
        scope.launch {
            chapters =
                try {
                    val id = mangaDexId ?: discovery.findOnMangaDex(title)?.id
                    if (id == null) {
                        availableOn = emptyList()
                        Loadable.Ready(emptyList())
                    } else {
                        if (mangaDexId == null) {
                            mangaDexId = id
                            if (library.contains(title)) library.setMangaDexId(title, id)
                        }
                        availableOn = listOf(MangaDexApi.SERVICE)
                        val list = mangaDex.allChapters(id, languages())
                        library.entry(title)?.let { library.recordCheck(it.key, list) }
                        Loadable.Ready(list)
                    }
                } catch (e: ApiError) {
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }
}
