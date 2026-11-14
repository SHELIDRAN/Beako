package org.nekomanga.shared.tracking

import kotlinx.coroutines.flow.StateFlow
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.model.Title

/** The signed-in account of a service. */
data class Account(val name: String, val avatarUrl: String? = null)

/** A title found on a tracker when linking. */
data class TrackerMatch(
    val remoteId: String,
    val title: String,
    val coverUrl: String?,
    val year: Int?,
)

/** One entry of a tracker list, for importing into the library. */
data class RemoteEntry(
    val title: Title,
    val track: Track,
    /** The newest released chapter number the tracker knows of (not a total). */
    val latestNumber: String? = null,
    /** When it was released, ISO-8601. */
    val latestAt: String? = null,
)

/**
 * A reading tracker (Comick, AniList). Implementations follow each service's rules; the shared
 * rules live in [TrackerSync].
 */
interface Tracker {
    /** A [org.nekomanga.shared.library.Trackers] id. */
    val service: String
    val displayName: String

    /** The signed-in account's profile, or null until it loads. */
    val account: StateFlow<Account?>

    /** True while sign-in secrets are stored, even if the profile hasn't loaded (e.g. offline). */
    val isConnected: Boolean

    /** Finds the tracker's id for [title]: by shared ids first, else a title search. */
    suspend fun find(title: Title): TrackerMatch?

    /** Searches the tracker by name, for linking by hand. */
    suspend fun search(query: String): List<TrackerMatch>

    /** The remote record, or null when the title isn't on the user's list. */
    suspend fun get(remoteId: String): Track?

    /** Creates or updates the remote record with these values (null leaves a value alone). */
    suspend fun save(remoteId: String, status: TrackStatus?, progress: String?, score: Int?): Track

    suspend fun remove(remoteId: String)

    /** The user's whole list, for importing. */
    suspend fun list(): List<RemoteEntry>

    /** Signs out and forgets every stored secret. */
    suspend fun disconnect()
}
