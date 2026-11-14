package org.nekomanga.desktop.state

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.tracking.Tracker
import org.nekomanga.shared.tracking.TrackerMatch

/** One tracker's state on a title page. [matches] is non-null while the link picker is open. */
data class TrackUi(
    val busy: Boolean = false,
    val error: String? = null,
    val matches: List<TrackerMatch>? = null,
)

/** Linking a title to Comick or AniList, and editing its status, chapter and score there. */
class TitleTracking(
    private val scope: CoroutineScope,
    val accounts: AccountsModel,
    private val details: DetailsModel,
) {
    val ui = mutableStateMapOf<String, TrackUi>()

    private val title
        get() = details.title

    /** Finds the title on [service] and links it; opens the picker when there's no sure match. */
    fun autoLink(service: String) {
        work(service) { tracker ->
            val match = tracker.find(title)
            if (match == null) {
                ui[service] = TrackUi(matches = tracker.search(title.title))
            } else {
                link(tracker, match)
            }
        }
    }

    private var searchJob: Job? = null

    /** Searches as the user types, waiting for a pause so each keystroke isn't a request. */
    fun search(service: String, query: String) {
        searchJob?.cancel()
        searchJob =
            work(service) { tracker ->
                delay(SEARCH_DELAY_MILLIS)
                ui[service] =
                    TrackUi(matches = if (query.isBlank()) emptyList() else tracker.search(query))
            }
    }

    fun pick(service: String, match: TrackerMatch) {
        work(service) { tracker -> link(tracker, match) }
    }

    fun openPicker(service: String) {
        ui[service] = TrackUi(matches = emptyList())
        search(service, title.title)
    }

    fun closePicker(service: String) {
        ui[service] = TrackUi()
    }

    fun setStatus(service: String, status: TrackStatus) {
        edit(service) { it.copy(status = status) }
    }

    fun setScore(service: String, score: Int?) {
        edit(service) { it.copy(score = score) }
    }

    /** Stops tracking here; with [removeRemote] also deletes the entry from the tracker's list. */
    fun unlink(service: String, removeRemote: Boolean) {
        work(service) { tracker ->
            val track = details.entry()?.tracks?.get(service)
            if (removeRemote && track != null) tracker.remove(track.remoteId)
            accounts.library.removeTrack(title, service)
        }
    }

    private suspend fun link(tracker: Tracker, match: TrackerMatch) {
        // Tracks live on library entries, so linking adds the title to the library.
        if (details.entry() == null) details.addToLibrary()
        accounts.sync.link(tracker, title, match.remoteId, details.readProgress())
        ui[tracker.service] = TrackUi()
    }

    private fun edit(service: String, change: (Track) -> Track) {
        work(service) {
            val track = details.entry()?.tracks?.get(service) ?: return@work
            accounts.saveTrack(title, track, change(track))
        }
    }

    private companion object {
        const val SEARCH_DELAY_MILLIS = 400L
    }

    private fun work(service: String, block: suspend (Tracker) -> Unit): Job? {
        val tracker = accounts.tracker(service) ?: return null
        val matches = ui[service]?.matches
        ui[service] = TrackUi(busy = true, matches = matches)
        return scope.launch {
            try {
                block(tracker)
                ui[service]?.let { if (it.busy) ui[service] = it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                ui[service] = TrackUi(error = e.message, matches = matches)
            }
        }
    }
}
