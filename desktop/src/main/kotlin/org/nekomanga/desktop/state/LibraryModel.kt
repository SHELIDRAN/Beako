package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.nekomanga.desktop.screens.normaliseForSearch
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.library.LibraryStore

enum class GroupBy(val label: String) {
    NONE("None"),
    STATUS("Reading status"),
    UNREAD("Unread"),
    DOWNLOADED("Downloaded"),
    PUBLICATION("Publication status"),
    TYPE("Type"),
    CATEGORY("Category"),
}

enum class LibrarySort(val label: String) {
    LAST_READ("Last read"),
    LATEST_CHAPTER("Latest chapter"),
    TITLE("Title"),
    DATE_ADDED("Date added"),
    UNREAD("Unread count"),
}

data class LibraryGroup(val name: String, val entries: List<LibraryEntry>)

/** Library screen state: search, category filter, grouping and sort over the stored library. */
class LibraryModel(
    val store: LibraryStore,
    /** Titles ("source:id") with downloaded chapters. */
    private val downloadedKeys: () -> Set<String> = { emptySet() },
) {
    /** Shows only titles with downloaded chapters (the "Downloaded only" mode). */
    var downloadedOnly by mutableStateOf(false)

    var query by mutableStateOf("")
    var categoryFilter by mutableStateOf<Set<String>>(emptySet())
    var groupBy by mutableStateOf(GroupBy.NONE)
    var sort by mutableStateOf(LibrarySort.LAST_READ)
    var descending by mutableStateOf(true)

    fun groups(entries: List<LibraryEntry>): List<LibraryGroup> {
        val downloaded =
            if (downloadedOnly || groupBy == GroupBy.DOWNLOADED) downloadedKeys() else emptySet()
        val visible =
            entries
                .filter(::matches)
                .filter { !downloadedOnly || it.key in downloaded }
                .sortedWith(comparator())
        return when (groupBy) {
            GroupBy.NONE -> listOf(LibraryGroup("", visible))
            GroupBy.STATUS -> visible.groupBy { it.status.label }.toGroups()
            GroupBy.UNREAD ->
                visible
                    .groupBy { if (it.unreadCount > 0) "Has unread chapters" else "Caught up" }
                    .toGroups()
            GroupBy.DOWNLOADED ->
                visible
                    .groupBy { if (it.key in downloaded) "Downloaded" else "Not downloaded" }
                    .toGroups()
            GroupBy.PUBLICATION ->
                visible.groupBy { it.title.status?.label ?: "Unknown" }.toGroups()
            GroupBy.TYPE -> visible.groupBy { it.title.type?.label ?: "Unknown" }.toGroups()
            GroupBy.CATEGORY ->
                visible
                    .flatMap { entry ->
                        entry.categories.ifEmpty { listOf(UNCATEGORISED) }.map { it to entry }
                    }
                    .groupBy({ it.first }, { it.second })
                    .toGroups()
        }.filter { it.entries.isNotEmpty() }
    }

    private fun Map<String, List<LibraryEntry>>.toGroups() =
        entries.sortedBy { it.key }.map { LibraryGroup(it.key, it.value) }

    /** Matches the title, every alternative title (English, romaji, native), tags and genres. */
    private fun matches(entry: LibraryEntry): Boolean {
        if (categoryFilter.isNotEmpty() && entry.categories.none { it in categoryFilter }) {
            return false
        }
        val needle = normaliseForSearch(query)
        if (needle.isEmpty()) return true
        val title = entry.title
        return (listOf(title.title) + title.altTitles + title.tags).any {
            normaliseForSearch(it).contains(needle)
        }
    }

    private fun comparator(): Comparator<LibraryEntry> {
        val base: Comparator<LibraryEntry> =
            when (sort) {
                LibrarySort.LAST_READ -> compareBy { it.progress?.updatedAt ?: 0L }
                LibrarySort.LATEST_CHAPTER ->
                    compareBy {
                        it.newChapters.maxOfOrNull { n -> n.chapter.publishedAt }
                            ?: it.title.lastUploadedAt.orEmpty()
                    }
                LibrarySort.TITLE -> compareBy { it.title.title.lowercase() }
                LibrarySort.DATE_ADDED -> compareBy { it.addedAt }
                LibrarySort.UNREAD -> compareBy { it.unreadCount }
            }
        return if (descending) base.reversed() else base
    }

    companion object {
        const val UNCATEGORISED = "No category"
    }
}
