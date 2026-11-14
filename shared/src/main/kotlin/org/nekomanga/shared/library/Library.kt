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

/** Services that track reading progress. */
object Trackers {
    const val COMICK = "comick"
    const val ANILIST = "anilist"
}

/** Beako's reading statuses, mapped to each tracker's own. */
@Serializable
enum class TrackStatus(val label: String) {
    READING("Reading"),
    COMPLETED("Completed"),
    ON_HOLD("On hold"),
    DROPPED("Dropped"),
    PLAN_TO_READ("Plan to read"),
}

/** A library title's record on a tracker. */
@Serializable
data class Track(
    val service: String,
    /** The tracker's id for the title: a Comick HID or an AniList media id. */
    val remoteId: String,
    val status: TrackStatus? = null,
    /** Last chapter read, as text so "12.5" survives. */
    val progress: String? = null,
    /** 1–10, or null when unrated. */
    val score: Int? = null,
    val remoteTitle: String? = null,
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
    /** Records on trackers, by service ([Trackers]). */
    val tracks: Map<String, Track> = emptyMap(),
    /** Scanlation groups whose chapters are hidden for this title. */
    val excludedGroups: Set<String> = emptySet(),
    /** Where chapters are read from: "mangadex" or an extension ("ext:<id>"); null picks one. */
    val readingSource: String? = null,
    /** The title's address on each extension it was found on, by source id. */
    val sourceLinks: Map<String, String> = emptyMap(),
) {
    val key: String
        get() = keyOf(title)

    /** [chapters] without those from hidden groups. */
    fun visible(chapters: List<Chapter>): List<Chapter> =
        if (excludedGroups.isEmpty()) chapters
        else chapters.filter { it.group == null || it.group !in excludedGroups }

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
