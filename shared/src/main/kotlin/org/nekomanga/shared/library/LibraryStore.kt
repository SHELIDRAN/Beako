package org.nekomanga.shared.library

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.BeakoJson

/**
 * The local library: saved titles, categories, read chapters, progress and history, kept in one
 * JSON file. Changes apply in memory at once and are saved in the background (quick changes such as
 * page turns are combined into one write), always with an atomic replace so a crash can't leave a
 * half-written file. Call [flush] before the app exits.
 *
 * A damaged file is kept as `library.json.broken` and the library starts empty. A file that can't
 * be read at all (still locked after retries) is never overwritten: the store stays read-only for
 * the session and reports [loadError].
 */
class LibraryStore(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Set when the library file couldn't be read; changes then aren't saved. */
    var loadError: IOException? = null
        private set

    private val writer = DebouncedWriter(file)
    private val state = MutableStateFlow(load())

    val data: StateFlow<LibraryData> = state.asStateFlow()

    val entries: List<LibraryEntry>
        get() = state.value.entries

    fun entry(title: Title): LibraryEntry? = entry(LibraryEntry.keyOf(title))

    fun entry(key: String): LibraryEntry? = state.value.entries.firstOrNull { it.key == key }

    fun contains(title: Title): Boolean = entry(title) != null

    fun add(title: Title, categories: List<String> = emptyList(), mangaDexId: String? = null) =
        update { data ->
            if (data.entries.any { it.key == LibraryEntry.keyOf(title) }) data
            else
                data.copy(
                    entries =
                        data.entries +
                            LibraryEntry(
                                title,
                                mangaDexId,
                                categories.distinct(),
                                addedAt = now(),
                            ),
                    categories = (data.categories + categories).distinct(),
                )
        }

    fun remove(title: Title) = update { data ->
        data.copy(entries = data.entries.filterNot { it.key == LibraryEntry.keyOf(title) })
    }

    fun setCategories(title: Title, categories: List<String>) = update { data ->
        data
            .withEntry(LibraryEntry.keyOf(title)) { it.copy(categories = categories.distinct()) }
            .copy(categories = (data.categories + categories).distinct())
    }

    fun addCategory(name: String) = update { data ->
        val clean = name.trim()
        if (clean.isEmpty() || clean in data.categories) data
        else data.copy(categories = data.categories + clean)
    }

    /** Removes a category and takes it off every title. */
    fun deleteCategory(name: String) = update { data ->
        data.copy(
            categories = data.categories - name,
            entries = data.entries.map { it.copy(categories = it.categories - name) },
        )
    }

    fun setTrack(title: Title, track: Track) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) {
            it.copy(tracks = it.tracks + (track.service to track))
        }
    }

    fun removeTrack(title: Title, service: String) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) { it.copy(tracks = it.tracks - service) }
    }

    /** Removes a tracker's records from every title (on disconnect). */
    fun removeAllTracks(service: String) = update { data ->
        data.copy(entries = data.entries.map { it.copy(tracks = it.tracks - service) })
    }

    fun setReadingSource(title: Title, sourceId: String) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) { it.copy(readingSource = sourceId) }
    }

    fun setSourceLinks(title: Title, links: Map<String, String>) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) { it.copy(sourceLinks = it.sourceLinks + links) }
    }

    fun setExcludedGroups(title: Title, groups: Set<String>) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) { it.copy(excludedGroups = groups) }
    }

    /**
     * Merges a restored library into this one. Titles already here keep their progress and tracker
     * links; read chapters, categories and hidden groups are combined. Returns how many titles were
     * added.
     */
    fun merge(incoming: LibraryData): Int {
        var added = 0
        update { data ->
            val byKey = data.entries.associateBy { it.key }.toMutableMap()
            // The same series may be here under another source (e.g. added from Comick): match its
            // MangaDex id too.
            val keyByMangaDexId =
                data.entries.mapNotNull { e -> e.mangaDexId?.let { it to e.key } }.toMap()
            for (incomingEntry in incoming.entries) {
                val key =
                    if (incomingEntry.key in byKey) incomingEntry.key
                    else incomingEntry.mangaDexId?.let { keyByMangaDexId[it] } ?: incomingEntry.key
                val entry = incomingEntry
                val current = byKey[key]
                byKey[key] =
                    if (current == null) {
                        added++
                        entry
                    } else {
                        current.copy(
                            mangaDexId = current.mangaDexId ?: entry.mangaDexId,
                            categories = (current.categories + entry.categories).distinct(),
                            readChapterIds = current.readChapterIds + entry.readChapterIds,
                            progress = current.progress ?: entry.progress,
                            tracks = entry.tracks + current.tracks,
                            excludedGroups = current.excludedGroups + entry.excludedGroups,
                        )
                    }
            }
            val history =
                (data.history + incoming.history)
                    .distinctBy { it.title.source to it.chapter.id }
                    .sortedByDescending { it.readAt }
                    .take(MAX_HISTORY_ON_MERGE)
            data.copy(
                entries = byKey.values.toList(),
                categories = (data.categories + incoming.categories).distinct(),
                history = history,
            )
        }
        return added
    }

    fun setMangaDexId(title: Title, mangaDexId: String) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) { it.copy(mangaDexId = mangaDexId) }
    }

    /**
     * Records the reader's position and adds it to history. Titles outside the library still get a
     * history entry, so history shows everything read.
     */
    fun recordProgress(title: Title, chapter: Chapter, page: Int, incognito: Boolean = false) {
        if (incognito) return
        val time = now()
        update { data ->
            val history =
                listOf(HistoryEntry(title, chapter, page, time)) +
                    data.history.filterNot {
                        LibraryEntry.keyOf(it.title) == LibraryEntry.keyOf(title)
                    }
            data
                .withEntry(LibraryEntry.keyOf(title)) {
                    it.copy(
                        progress = Progress(chapter.id, chapter.number, page, time),
                        newChapters = it.newChapters.filterNot { n -> n.chapter.id == chapter.id },
                    )
                }
                .copy(history = history.take(MAX_HISTORY))
        }
    }

    fun setRead(title: Title, chapterIds: Collection<String>, read: Boolean) = update { data ->
        data.withEntry(LibraryEntry.keyOf(title)) {
            it.copy(
                readChapterIds =
                    if (read) it.readChapterIds + chapterIds
                    else it.readChapterIds - chapterIds.toSet(),
                newChapters =
                    if (read) it.newChapters.filterNot { n -> n.chapter.id in chapterIds }
                    else it.newChapters,
            )
        }
    }

    fun removeFromHistory(title: Title) = update { data ->
        data.copy(
            history =
                data.history.filterNot {
                    LibraryEntry.keyOf(it.title) == LibraryEntry.keyOf(title)
                },
        )
    }

    fun clearHistory() = update { it.copy(history = emptyList()) }

    /**
     * Stores the result of an update check. Chapters not seen before become new chapters, except on
     * the first check, which only records what exists. Returns the chapters that are new.
     */
    fun recordCheck(key: String, chapters: List<Chapter>): List<Chapter> {
        var found = emptyList<Chapter>()
        val time = now()
        update { data ->
            data.withEntry(key) { entry ->
                val firstCheck = entry.lastCheckedAt == null
                val fresh =
                    if (firstCheck) emptyList()
                    else
                        chapters.filter {
                            it.id !in entry.knownChapterIds && it.id !in entry.readChapterIds
                        }
                found = fresh
                entry.copy(
                    knownChapterIds = chapters.mapTo(mutableSetOf()) { it.id },
                    chapterCount = chapters.size,
                    newChapters =
                        (fresh.map { NewChapter(it, time) } + entry.newChapters).take(MAX_NEW),
                    lastCheckedAt = time,
                )
            }
        }
        return found
    }

    private fun LibraryData.withEntry(key: String, change: (LibraryEntry) -> LibraryEntry) =
        copy(entries = entries.map { if (it.key == key) change(it) else it })

    @Synchronized
    private fun update(change: (LibraryData) -> LibraryData) {
        val before = state.value
        val after = change(before)
        if (after == before) return
        state.value = after
        save(after)
    }

    private fun load(): LibraryData {
        val text =
            try {
                readWithRetry(file) ?: return LibraryData()
            } catch (e: IOException) {
                loadError = e
                return LibraryData()
            }
        return try {
            BeakoJson.decodeFromString(LibraryData.serializer(), text)
        } catch (e: SerializationException) {
            keepBroken()
            LibraryData()
        } catch (e: IllegalArgumentException) {
            keepBroken()
            LibraryData()
        }
    }

    private fun keepBroken() {
        runCatching {
            Files.copy(
                file.toPath(),
                File(file.parentFile, file.name + ".broken").toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun save(data: LibraryData) {
        if (loadError != null) return
        writer.write(BeakoJson.encodeToString(LibraryData.serializer(), data))
    }

    /** Writes any pending change now. */
    fun flush() = writer.flush()

    /** The last failed save, if any, for the UI to report. */
    val lastSaveError: IOException?
        get() = writer.lastError

    companion object {
        const val MAX_HISTORY = 500
        const val MAX_NEW = 200
    }
}

private const val MAX_HISTORY_ON_MERGE = 1_000
