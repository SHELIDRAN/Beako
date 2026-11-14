package org.nekomanga.shared.library

import kotlinx.serialization.Serializable
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title

/** Reading status of a library title, as the library groups and filters it. */
@Serializable
enum class ReadingStatus(val label: String) {
    NOT_STARTED("Not started"),
    READING("Reading"),
    COMPLETED("Completed"),
}

/** Where the reader left off in a title. */
@Serializable
data class Progress(
    val chapterId: String,
    /** Chapter number as text, e.g. "12.5", for display and tracker sync. */
    val chapterNumber: String?,
    val page: Int,
    /** Milliseconds since the epoch. */
    val updatedAt: Long,
)

/** A chapter the update check found and the user hasn't opened yet. */
@Serializable
data class NewChapter(
    val chapter: Chapter,
    /** When it was found, ms since the epoch. */
    val foundAt: Long,
)

@Serializable
data class LibraryEntry(
    /** The title as discovery found it (Comick or MangaDex), kept for display offline. */
    val title: Title,
    /** The MangaDex id used for chapters and update checks, once found. */
    val mangaDexId: String? = null,
    val categories: List<String> = emptyList(),
    /** Milliseconds since the epoch. */
    val addedAt: Long,
    /** Ids of chapters read to the end. */
    val readChapterIds: Set<String> = emptySet(),
    val progress: Progress? = null,
    /** Every chapter known at the last check, so new ones can be told apart. */
    val knownChapterIds: Set<String> = emptySet(),
    val newChapters: List<NewChapter> = emptyList(),
    /** Chapters known at the last check (for the unread count). */
    val chapterCount: Int = 0,
    val lastCheckedAt: Long? = null,
) {
    val key: String
        get() = keyOf(title)

    val unreadCount: Int
        get() = (chapterCount - readChapterIds.size).coerceAtLeast(0)

    val status: ReadingStatus
        get() =
            when {
                readChapterIds.isEmpty() && progress == null -> ReadingStatus.NOT_STARTED
                chapterCount > 0 && readChapterIds.size >= chapterCount -> ReadingStatus.COMPLETED
                else -> ReadingStatus.READING
            }

    companion object {
        fun keyOf(title: Title): String = "${title.source}:${title.id}"
    }
}

/** One opened chapter, newest first in the history list. */
@Serializable
data class HistoryEntry(
    val title: Title,
    val chapter: Chapter,
    val page: Int,
    /** Milliseconds since the epoch. */
    val readAt: Long,
)

/** Everything the library file holds. */
@Serializable
data class LibraryData(
    val version: Int = 1,
    val entries: List<LibraryEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
)
