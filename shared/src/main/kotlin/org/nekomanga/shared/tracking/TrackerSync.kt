package org.nekomanga.shared.tracking

import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.model.Title

/**
 * The tracking rules every tracker shares:
 * - Linking keeps what the tracker already has, and adds the title as Reading only when missing.
 * - Progress only ever moves forward: Beako never lowers a tracker's saved chapter.
 * - Finishing a chapter pushes its number, and moves Plan to read to Reading.
 */
class TrackerSync(private val library: LibraryStore) {

    /** Links [title] to [remoteId] on [tracker] and stores the record. */
    suspend fun link(
        tracker: Tracker,
        title: Title,
        remoteId: String,
        localProgress: String?,
    ): Track {
        val existing = tracker.get(remoteId)
        val track =
            when {
                existing == null ->
                    tracker.save(remoteId, TrackStatus.READING, localProgress, score = null)
                isAhead(localProgress, existing.progress) ->
                    tracker.save(remoteId, existing.status, localProgress, existing.score)
                else -> existing
            }
        library.setTrack(title, track)
        return track
    }

    /** Pushes a finished chapter to every linked tracker. Returns the records it updated. */
    suspend fun chapterRead(
        trackers: List<Tracker>,
        title: Title,
        chapterNumber: String?,
    ): List<Track> {
        if (chapterNumber == null) return emptyList()
        val entry = library.entry(title) ?: return emptyList()
        val updated = mutableListOf<Track>()
        for (tracker in trackers) {
            val local = entry.tracks[tracker.service] ?: continue
            if (tracker.account.value == null || !isAhead(chapterNumber, local.progress)) continue
            // Re-read the remote first, so progress made elsewhere is never lowered.
            val remote = tracker.get(local.remoteId)
            if (remote != null && !isAhead(chapterNumber, remote.progress)) {
                library.setTrack(title, remote)
                continue
            }
            val status =
                when (remote?.status ?: local.status) {
                    null,
                    TrackStatus.PLAN_TO_READ -> TrackStatus.READING
                    else -> remote?.status ?: local.status
                }
            val track =
                tracker.save(local.remoteId, status, chapterNumber, remote?.score ?: local.score)
            library.setTrack(title, track)
            updated += track
        }
        return updated
    }

    companion object {
        /**
         * True when [candidate] is a later chapter than [current]. Unknown or non-numeric progress
         * on the tracker is preserved, so a non-number never counts as ahead of it.
         */
        fun isAhead(candidate: String?, current: String?): Boolean {
            val next = candidate?.toDoubleOrNull() ?: return false
            if (current == null) return true
            val now = current.toDoubleOrNull() ?: return false
            return next > now
        }
    }
}
