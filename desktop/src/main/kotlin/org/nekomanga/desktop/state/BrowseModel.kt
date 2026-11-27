package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.comick.ComickChart
import org.nekomanga.shared.comick.ComickUpdate
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.mangadex.MangaDexOrder
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/** State of the Browse home: Comick's charts, with MangaDex shelves when Comick is down. */
class BrowseModel(
    private val scope: CoroutineScope,
    private val comick: ComickApi,
    private val mangaDex: MangaDexApi,
    /** The language of chapter updates: the first reading language. */
    private val language: () -> String = { "en" },
) {
    /** Updates: Hot (most read) or New (just released). */
    var updatesHot by mutableStateOf(true)
        private set

    /** Types to show in Updates; empty shows all. */
    var updatesTypes by mutableStateOf<Set<ComicType>>(emptySet())
        private set

    val updates = mutableStateListOf<ComickUpdate>()
    var updatesStatus by mutableStateOf<Loadable<Unit>>(Loadable.Loading)
        private set

    /** More pages can be asked for (Comick stops at page 100). */
    var updatesMore by mutableStateOf(false)
        private set

    private var updatesPage = 0
    private var updatesJob: Job? = null

    /** Chart windows offered by the droplet switch, with their labels. */
    val windows = listOf(7 to "7d", 30 to "1m", 90 to "3m")

    var windowIndex by mutableStateOf(0)
        private set

    var popularWindowIndex by mutableStateOf(0)
        private set

    var mostFollowedNew by mutableStateOf<Loadable<List<Title>>>(Loadable.Loading)
        private set

    var recentPopular by mutableStateOf<Loadable<List<Title>>>(Loadable.Loading)
        private set

    /** Set when Comick can't be reached; the screen shows MangaDex shelves and a retry banner. */
    var comickDown by mutableStateOf(false)
        private set

    var mangaDexPopular by mutableStateOf<Loadable<List<Title>>>(Loadable.Loading)
        private set

    var mangaDexLatest by mutableStateOf<Loadable<List<Title>>>(Loadable.Loading)
        private set

    private var started = false
    private var followedJob: Job? = null
    private var popularJob: Job? = null

    fun start() {
        if (!started) {
            started = true
            refresh()
        }
    }

    fun refresh() {
        comickDown = false
        loadMostFollowedNew()
        loadRecentPopular()
        loadUpdates(reset = true)
    }

    fun selectUpdates(hot: Boolean) {
        if (hot == updatesHot) return
        updatesHot = hot
        loadUpdates(reset = true)
    }

    fun toggleUpdatesType(type: ComicType) {
        updatesTypes = if (type in updatesTypes) updatesTypes - type else updatesTypes + type
        loadUpdates(reset = true)
    }

    fun moreUpdates() {
        if (updatesJob?.isActive != true && updatesMore) loadUpdates(reset = false)
    }

    private fun loadUpdates(reset: Boolean) {
        updatesJob?.cancel()
        if (reset) {
            updates.clear()
            updatesPage = 0
            updatesStatus = Loadable.Loading
        }
        updatesJob = scope.launch {
            try {
                // With a type chosen, a page can hold no matches: read on (a few pages at most)
                // until something matches or Comick runs out.
                var page = updatesPage
                var more: Boolean
                var found: List<ComickUpdate>
                var tries = 0
                do {
                    page++
                    tries++
                    val result = comick.updates(updatesHot, page, language(), updatesTypes)
                    found = result.updates
                    more = result.more
                } while (found.isEmpty() && more && tries < PAGES_PER_LOAD)
                // A title can have several new chapters; it's listed once, at its newest.
                val shown = updates.mapTo(HashSet()) { it.title.id }
                updates += found.filter { shown.add(it.title.id) }
                updatesPage = page
                updatesMore = more
                updatesStatus = Loadable.Ready(Unit)
            } catch (e: ApiError) {
                if (updates.isEmpty()) updatesStatus = Loadable.Failed(e.message.orEmpty())
                updatesMore = updates.isNotEmpty()
            }
        }
    }

    fun selectPopularWindow(index: Int) {
        if (index == popularWindowIndex) return
        popularWindowIndex = index
        loadRecentPopular()
    }

    private fun loadRecentPopular() {
        popularJob?.cancel()
        recentPopular = Loadable.Loading
        val days = windows[popularWindowIndex].first
        popularJob = scope.launch { recentPopular = chart(ComickChart.MOST_RECENT_POPULAR, days) }
    }

    fun selectWindow(index: Int) {
        if (index == windowIndex) return
        windowIndex = index
        loadMostFollowedNew()
    }

    private fun loadMostFollowedNew() {
        followedJob?.cancel()
        mostFollowedNew = Loadable.Loading
        val days = windows[windowIndex].first
        followedJob = scope.launch { mostFollowedNew = chart(ComickChart.MOST_FOLLOWED_NEW, days) }
    }

    private suspend fun chart(chart: ComickChart, days: Int): Loadable<List<Title>> =
        try {
            Loadable.Ready(comick.chart(chart, days))
        } catch (e: ApiError) {
            if (e.isOutage) showMangaDex()
            Loadable.Failed(e.message.orEmpty())
        }

    private fun showMangaDex() {
        if (comickDown) return
        comickDown = true
        mangaDexPopular = Loadable.Loading
        mangaDexLatest = Loadable.Loading
        scope.launch { mangaDexPopular = mangaDexShelf(MangaDexOrder.MOST_FOLLOWED) }
        scope.launch { mangaDexLatest = mangaDexShelf(MangaDexOrder.LATEST_UPLOAD) }
    }

    private suspend fun mangaDexShelf(order: MangaDexOrder): Loadable<List<Title>> =
        try {
            Loadable.Ready(mangaDex.search(query = null, limit = 20, order = order).items)
        } catch (e: ApiError) {
            Loadable.Failed(e.message.orEmpty())
        }

    private companion object {
        /** Pages read for one load when the type filter leaves them empty. */
        const val PAGES_PER_LOAD = 4
    }
}
