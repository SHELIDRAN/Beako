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
    /** Sort by title ignoring a leading "a", "an" or "the". */
    private val ignoreArticles: () -> Boolean = { false },
    /** Groups folded away last time, and where to save changes to them. */
    initialCollapsed: Set<String> = emptySet(),
    private val saveCollapsed: (Set<String>) -> Unit = {},
) {
    /** Folded groups, keyed by grouping and name, so each grouping keeps its own. */
    var collapsed by mutableStateOf(initialCollapsed)
        private set

    private fun keyOf(group: LibraryGroup) = "${groupBy.name}:${group.name}"

    fun isCollapsed(group: LibraryGroup): Boolean = keyOf(group) in collapsed

    /** Folds or unfolds one group. */
    fun toggle(group: LibraryGroup) {
        val key = keyOf(group)
        saveFolded(if (key in collapsed) collapsed - key else collapsed + key)
    }

    /** Folds every one of [groups], or unfolds them all when they're all folded already. */
    fun toggleAll(groups: List<LibraryGroup>) {
        val keys = groups.filter { it.name.isNotEmpty() }.map(::keyOf).toSet()
        saveFolded(if (collapsed.containsAll(keys)) collapsed - keys else collapsed + keys)
    }

    fun allCollapsed(groups: List<LibraryGroup>): Boolean =
        groups
            .filter { it.name.isNotEmpty() }
            .let { named -> named.isNotEmpty() && named.all { keyOf(it) in collapsed } }

    private fun saveFolded(next: Set<String>) {
        collapsed = next
        saveCollapsed(next)
    }

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
                LibrarySort.TITLE ->
                    compareBy {
                        val name = it.title.title.lowercase()
                        if (ignoreArticles()) withoutArticle(name) else name
                    }
                LibrarySort.DATE_ADDED -> compareBy { it.addedAt }
                LibrarySort.UNREAD -> compareBy { it.unreadCount }
            }
        return if (descending) base.reversed() else base
    }

    companion object {
        const val UNCATEGORISED = "No category"
    }
}

/** "the apothecary diaries" → "apothecary diaries": articles don't decide alphabetical order. */
fun withoutArticle(name: String): String {
    for (article in listOf("the ", "an ", "a ")) {
        if (name.startsWith(article) && name.length > article.length)
            return name.removePrefix(article)
    }
    return name
}
