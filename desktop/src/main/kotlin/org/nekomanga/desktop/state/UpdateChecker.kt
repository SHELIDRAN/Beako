package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/** New chapters found for one title in a check. */
data class TitleUpdate(val title: Title, val chapters: List<Chapter>)

/**
 * Checks every library title for new chapters, one title at a time so MangaDex's rate limits hold.
 * Runs shortly after launch and then every [intervalMillis] while the app is open (this replaces
 * Android's WorkManager job); [onFound] is told about each run's new chapters, for a notification.
 */
class UpdateChecker(
    private val scope: CoroutineScope,
    private val library: LibraryStore,
    private val mangaDex: MangaDexApi,
    private val discovery: Discovery,
    private val intervalMillis: Long = 6 * 60 * 60 * 1000L,
    private val firstDelayMillis: Long = 15_000L,
    private val onFound: (List<TitleUpdate>) -> Unit = {},
) {
    var running by mutableStateOf(false)
        private set

    /** Titles checked so far in the current run, and how many there are. */
    var progress by mutableStateOf(0 to 0)
        private set

    var lastRunAt by mutableStateOf<Long?>(null)
        private set

    /** Titles that couldn't be checked in the last run. */
    var failures by mutableStateOf(0)
        private set

    private var job: Job? = null
    private var schedule: Job? = null

    /** Starts the periodic checks. */
    fun start() {
        if (schedule != null) return
        schedule = scope.launch {
            delay(firstDelayMillis)
            while (isActive) {
                runNow()?.join()
                delay(intervalMillis)
            }
        }
    }

    /** Starts a check unless one is running; returns its job. */
    fun runNow(): Job? {
        if (running) return job
        job = scope.launch { check() }
        return job
    }

    private suspend fun check() {
        running = true
        failures = 0
        val entries = library.entries
        progress = 0 to entries.size
        val found = mutableListOf<TitleUpdate>()
        try {
            for ((index, entry) in entries.withIndex()) {
                try {
                    val id =
                        entry.mangaDexId
                            ?: discovery.findOnMangaDex(entry.title)?.id?.also {
                                library.setMangaDexId(entry.title, it)
                            }
                    if (id != null) {
                        val fresh = library.recordCheck(entry.key, mangaDex.allChapters(id))
                        if (fresh.isNotEmpty()) found += TitleUpdate(entry.title, fresh)
                    }
                } catch (e: ApiError) {
                    failures += 1
                }
                progress = index + 1 to entries.size
            }
        } finally {
            running = false
            lastRunAt = System.currentTimeMillis()
        }
        if (found.isNotEmpty()) onFound(found)
    }

    companion object {
        /** A one-line summary for a notification, e.g. "Eleceed 391 and 2 more titles". */
        fun summary(updates: List<TitleUpdate>): String {
            val count = updates.sumOf { it.chapters.size }
            val first = updates.first()
            val number = first.chapters.firstOrNull()?.number?.let { " $it" }.orEmpty()
            val more = updates.size - 1
            val head = "$count new chapter${if (count == 1) "" else "s"}"
            return if (more == 0) "$head: ${first.title.title}$number"
            else
                "$head: ${first.title.title}$number and $more more title${if (more == 1) "" else "s"}"
        }
    }
}
