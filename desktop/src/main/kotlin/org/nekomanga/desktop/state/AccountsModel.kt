package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nekomanga.shared.auth.AuthCancelled
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.Trackers
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexCredentials
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.tracking.RemoteEntry
import org.nekomanga.shared.tracking.Tracker
import org.nekomanga.shared.tracking.TrackerSync
import org.nekomanga.shared.tracking.anilist.AniListTracker
import org.nekomanga.shared.tracking.comick.ComickAccount
import org.nekomanga.shared.tracking.comick.ComickTracker

/** The services Beako signs in to. */
enum class Service(val label: String) {
    COMICK("Comick"),
    ANILIST("AniList"),
    MANGADEX("MangaDex"),
}

/** What a service's row shows while it works or after it fails. */
data class ServiceStatus(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Sign-in, sign-out, imports and progress sync for Comick, AniList and MangaDex. Work runs on
 * [scope]; each service reports its progress through [status].
 */
class AccountsModel(
    private val scope: CoroutineScope,
    val comickAccount: ComickAccount,
    val comick: ComickTracker,
    val aniList: AniListTracker,
    val mangaDex: MangaDexAccount,
    val library: LibraryStore,
    private val settings: SettingsStore,
    private val openBrowser: (String) -> Unit,
) {
    val sync = TrackerSync(library)
    val trackers: List<Tracker> = listOf(comick, aniList)

    val status = mutableStateMapOf<Service, ServiceStatus>()
    private val jobs = mutableMapOf<Service, Job>()

    /** The last progress push that failed, shown on the title page; null when all went through. */
    var syncProblem by mutableStateOf<String?>(null)
        private set

    /** Comick library titles with chapters past your progress; null until first loaded. */
    var comickReleases by mutableStateOf<Loadable<List<RemoteEntry>>?>(null)
        private set

    private var releasesLoadedAt = 0L
    private var releasesJob: Job? = null

    /**
     * Loads [comickReleases]. Skipped when fresh (the whole library list is several requests),
     * unless [force]d by a manual refresh.
     */
    fun loadComickReleases(force: Boolean = false, now: Long = System.currentTimeMillis()) {
        if (!comickAccount.connected.value) {
            comickReleases = null
            return
        }
        if (releasesJob?.isActive == true) return
        if (!force && comickReleases is Loadable.Ready && now - releasesLoadedAt < RELEASES_TTL)
            return
        if (comickReleases !is Loadable.Ready) comickReleases = Loadable.Loading
        releasesJob = scope.launch {
            comickReleases =
                try {
                    Loadable.Ready(comick.newReleases()).also { releasesLoadedAt = now }
                } catch (e: ApiError) {
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }

    fun tracker(service: String): Tracker? = trackers.firstOrNull { it.service == service }

    /** Loads account names and avatars for services signed in on an earlier run. */
    fun restore() {
        mangaDex.restore(
            settings.current.mangaDexClientId.ifBlank { null },
            settings.current.mangaDexUsername.ifBlank { null },
        )
        scope.launch { quietly { comick.refreshAccount() } }
        scope.launch { quietly { aniList.refreshAccount() } }
    }

    fun connectComick() =
        run(Service.COMICK, BROWSER_HINT, done = { "Connected" }) {
            comickAccount.signIn(openBrowser)
            comick.refreshAccount()
        }

    fun connectAniList(clientId: String) {
        if (clientId != AniListTracker.BEAKO_CLIENT_ID)
            settings.update { it.copy(aniListClientId = clientId.trim()) }
        run(Service.ANILIST, BROWSER_HINT, done = { "Connected" }) {
            aniList.signIn(clientId, openBrowser)
        }
    }

    fun signInMangaDex(credentials: MangaDexCredentials) =
        run(Service.MANGADEX, "Signing in…", done = { "Signed in" }) {
            mangaDex.signIn(credentials)
            settings.update {
                it.copy(
                    mangaDexClientId = credentials.clientId.trim(),
                    mangaDexUsername = credentials.username.trim(),
                )
            }
        }

    /** Stops a sign-in that is waiting for the browser. */
    fun cancel(service: Service) {
        jobs.remove(service)?.cancel()
        status[service] = ServiceStatus()
    }

    fun disconnect(service: Service) =
        run(service, "Disconnecting…", done = { "Disconnected" }) {
            when (service) {
                Service.COMICK -> {
                    releasesJob?.cancel()
                    comick.disconnect()
                    library.removeAllTracks(Trackers.COMICK)
                    comickReleases = null
                }
                Service.ANILIST -> {
                    aniList.disconnect()
                    library.removeAllTracks(Trackers.ANILIST)
                }
                Service.MANGADEX -> {
                    mangaDex.signOut()
                    settings.update { it.copy(mangaDexUsername = "") }
                }
            }
        }

    /**
     * Adds every title on the service's list to the library, keeping titles already there, and
     * links each to its tracker record.
     */
    fun import(service: Service) =
        run(
            service,
            "Importing… this can take a minute for long lists.",
            done = { count -> "Imported $count titles" },
        ) {
            when (service) {
                Service.COMICK -> importTracked(comick)
                Service.ANILIST -> importTracked(aniList)
                Service.MANGADEX -> {
                    val follows = mangaDex.follows()
                    val known = library.data.value.entries.mapNotNull { it.mangaDexId }.toSet()
                    follows.forEach { title ->
                        // Skip titles already in the library from any source (matched by MangaDex
                        // id).
                        if (!library.contains(title) && title.id !in known) {
                            library.add(title, mangaDexId = title.id)
                        }
                    }
                    follows.size
                }
            }
        }

    private suspend fun importTracked(tracker: Tracker): Int {
        val entries = tracker.list()
        for (entry in entries) {
            val existing = existingFor(entry.title, tracker.service, entry.track.remoteId)
            val title = existing ?: entry.title.also { library.add(it) }
            library.setTrack(title, entry.track)
        }
        return entries.size
    }

    /** A library title already linked to (or the same as) a tracker record. */
    private fun existingFor(title: Title, service: String, remoteId: String): Title? {
        library.entry(title)?.let {
            return it.title
        }
        val names =
            (listOf(title.title) + title.altTitles)
                .map(::normalise)
                .filter { it.isNotEmpty() }
                .toSet()
        return library.data.value.entries
            .firstOrNull { entry ->
                entry.tracks[service]?.remoteId == remoteId ||
                    normalise(entry.title.title) in names ||
                    (service == Trackers.COMICK &&
                        entry.title.source == Sources.COMICK &&
                        entry.title.id == remoteId)
            }
            ?.title
    }

    /**
     * Called when chapters are finished (in the reader, or marked read on the title page): pushes
     * the highest chapter to linked trackers and marks them read on MangaDex. Failures are kept in
     * [syncProblem], never thrown at the caller.
     */
    fun chaptersRead(title: Title, chapters: List<Chapter>) {
        val current = settings.current
        if (current.incognito || chapters.isEmpty()) return
        val highest = chapters.highestNumber()
        scope.launch {
            // The profile may not have loaded at start-up (offline); try again now.
            if (comick.isConnected && comick.account.value == null)
                quietly { comick.refreshAccount() }
            val problems = mutableListOf<String>()
            if (current.autoTrack && highest != null) {
                try {
                    sync.chapterRead(trackers, title, highest)
                } catch (e: ApiError) {
                    problems += e.message.orEmpty()
                }
            }
            val mangaId = library.entry(title)?.mangaDexId
            val mangaDexChapters = chapters.filter { it.source == Sources.MANGADEX }.map { it.id }
            if (
                current.syncReadToMangaDex &&
                    mangaDex.username.value != null &&
                    mangaId != null &&
                    mangaDexChapters.isNotEmpty()
            ) {
                try {
                    mangaDexChapters.chunked(100).forEach { mangaDex.markRead(mangaId, it) }
                } catch (e: ApiError) {
                    problems += e.message.orEmpty()
                }
            }
            syncProblem = problems.firstOrNull()
        }
    }

    /** Saves a change made on the title page and stores the tracker's answer. */
    suspend fun saveTrack(title: Title, track: Track, change: Track): Track {
        val tracker = tracker(track.service) ?: return track
        val saved =
            tracker.save(
                track.remoteId,
                change.status.takeIf { it != track.status },
                change.progress.takeIf { it != track.progress },
                change.score.takeIf { it != track.score },
            )
        library.setTrack(title, saved)
        return saved
    }

    private fun <T> run(
        service: Service,
        busy: String,
        done: (T) -> String,
        work: suspend () -> T,
    ) {
        jobs[service]?.cancel()
        status[service] = ServiceStatus(busy = true, message = busy)
        jobs[service] = scope.launch {
            status[service] =
                try {
                    ServiceStatus(message = done(work()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AuthCancelled) {
                    ServiceStatus(message = e.message, isError = true)
                } catch (e: ApiError) {
                    ServiceStatus(message = e.message, isError = true)
                } catch (e: IllegalArgumentException) {
                    ServiceStatus(message = e.message, isError = true)
                } catch (e: java.io.IOException) {
                    ServiceStatus(message = "Couldn’t finish: ${e.message}", isError = true)
                }
        }
    }

    private fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        const val RELEASES_TTL = 30 * 60_000L
        const val BROWSER_HINT = "Waiting for you to finish signing in in your browser…"
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (e: ApiError) {
            // Shown as signed out until the next attempt; the token is kept unless it was revoked.
        } catch (e: java.io.IOException) {}
    }
}
