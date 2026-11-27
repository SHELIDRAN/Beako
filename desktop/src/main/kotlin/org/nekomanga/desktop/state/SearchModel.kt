package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.comick.ComickGenre
import org.nekomanga.shared.comick.ComickSort
import org.nekomanga.shared.comick.ComickTag
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.discovery.FallbackReason
import org.nekomanga.shared.discovery.SearchOutcome
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/**
 * State of the Search page. Filter changes apply after a short pause, so typing or ticking several
 * boxes makes one request instead of many.
 */
class SearchModel(
    private val scope: CoroutineScope,
    private val discovery: Discovery,
    private val comick: ComickApi,
    private val debounceMillis: Long = 400,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
    /** The filters saved from last time (keyword left out), or null. */
    initialFilters: ComickFilters? = null,
    /** Saves the filters (without the keyword) whenever they change. */
    private val rememberFilters: (ComickFilters) -> Unit = {},
) {
    var filters by
        mutableStateOf(
            initialFilters?.copy(query = "", page = 1) ?: ComickFilters(sort = ComickSort.LATEST),
        )
        private set

    var titles by mutableStateOf<List<Title>>(emptyList())
        private set

    /** State of the first page; [titles] holds what has loaded. */
    var status by mutableStateOf<Loadable<Unit>>(Loadable.Loading)
        private set

    var servedBy by mutableStateOf(Sources.COMICK)
        private set

    var fallback by mutableStateOf<FallbackReason?>(null)
        private set

    var hasMore by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var tags by mutableStateOf<Loadable<List<ComickTag>>>(Loadable.Loading)
        private set

    var genres by mutableStateOf<Loadable<List<ComickGenre>>>(Loadable.Loading)
        private set

    /** True while the results are random picks for the current filters (see [randomPicks]). */
    var randomPicks by mutableStateOf(false)
        private set

    /** True while the user has chosen to search MangaDex directly. */
    var mangaDexByHand by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var moreJob: Job? = null

    /** Whether a by-hand MangaDex search was on before random picks, for going back. */
    private var mangaDexBeforeRandom = false
    private var started = false

    /**
     * Called as the page opens. The first time it searches for [query]; a [preset] (from Browse)
     * replaces the filters first, so only one search runs, also when the page was open before.
     */
    fun start(query: String = "", preset: ComickFilters? = null) {
        if (started) {
            if (preset != null) {
                randomPicks = false
                filters = preset.copy(query = filters.query, page = 1)
                rememberFilters(filters.copy(query = "", page = 1))
                run(immediately = true)
            }
            return
        }
        started = true
        filters = (preset ?: filters).copy(query = query, page = 1)
        if (preset != null) rememberFilters(filters.copy(query = "", page = 1))
        run(immediately = true)
        loadTagLists()
    }

    fun update(change: (ComickFilters) -> ComickFilters) {
        randomPicks = false
        filters = change(filters).copy(page = 1)
        rememberFilters(filters.copy(query = "", page = 1))
        run(immediately = false)
    }

    fun retryComick() {
        mangaDexByHand = false
        randomPicks = false
        loadTagLists()
        run(immediately = true)
    }

    fun searchMangaDex() {
        mangaDexByHand = true
        run(immediately = true)
    }

    /** Uses a saved preset's filters, keeping what's typed in the search box. */
    fun applyPreset(preset: ComickFilters) {
        update { preset.copy(query = it.query) }
    }

    /**
     * Random titles that match the current tags, genres, types and other filters: a few Comick
     * pages picked at random, mixed and cut to [count]. Shuffling again gives a new set.
     */
    fun randomPicks(count: Int = 12) {
        job?.cancel()
        cancelMore()
        if (!randomPicks) mangaDexBeforeRandom = mangaDexByHand
        mangaDexByHand = false
        randomPicks = true
        job = scope.launch {
            status = Loadable.Loading
            status =
                try {
                    titles = pickRandom(filters.copy(page = 1), count)
                    servedBy = Sources.COMICK
                    fallback = null
                    hasMore = false
                    Loadable.Ready(Unit)
                } catch (e: ApiError) {
                    // Stays in random picks, so Try again shuffles again.
                    titles = emptyList()
                    hasMore = false
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }

    /** Leaves random picks for the search as it was (Comick, or MangaDex if chosen by hand). */
    fun backToResults() {
        mangaDexByHand = mangaDexBeforeRandom
        run(immediately = true)
    }

    private fun cancelMore() {
        moreJob?.cancel()
        loadingMore = false
    }

    private suspend fun pickRandom(base: ComickFilters, count: Int): List<Title> {
        val found = LinkedHashMap<String, Title>()
        // Pages past the end come back empty: each miss lowers the highest page tried.
        var highest = RANDOM_PAGES
        var tries = 0
        while (found.size < count && highest >= 1 && tries < RANDOM_TRIES) {
            tries++
            val page = random.nextInt(1, highest + 1)
            val items = comick.search(base.copy(page = page, limit = RANDOM_PAGE_SIZE))
            // With "Others" chosen, types are filtered after the page arrives, so an empty page
            // isn't necessarily past the end.
            if (items.isEmpty() && !base.filtersTypesLocally) highest = page - 1
            else items.forEach { found.putIfAbsent("${it.source}:${it.id}", it) }
        }
        if (found.isEmpty() && highest >= 1) {
            comick.search(base.copy(page = 1, limit = RANDOM_PAGE_SIZE)).forEach {
                found.putIfAbsent("${it.source}:${it.id}", it)
            }
        }
        return found.values.shuffled(random).take(count)
    }

    fun loadMore() {
        if (!hasMore || loadingMore || status !is Loadable.Ready) return
        loadingMore = true
        val next = filters.copy(page = filters.page + 1)
        moreJob = scope.launch {
            try {
                val outcome =
                    if (servedBy == Sources.MANGADEX) discovery.searchMangaDex(next, fallback)
                    else discovery.search(next)
                filters = next
                titles = titles + outcome.titles
                hasMore = outcome.hasMore && outcome.titles.isNotEmpty()
            } catch (e: ApiError) {
                // Keep what is shown; the next scroll to the end tries again.
            } finally {
                loadingMore = false
            }
        }
    }

    private fun run(immediately: Boolean) {
        job?.cancel()
        cancelMore()
        randomPicks = false
        job = scope.launch {
            if (!immediately) delay(debounceMillis)
            status = Loadable.Loading
            status =
                try {
                    show(
                        if (mangaDexByHand) discovery.searchMangaDex(filters)
                        else discovery.search(filters),
                    )
                    Loadable.Ready(Unit)
                } catch (e: ApiError) {
                    titles = emptyList()
                    hasMore = false
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }

    private fun show(outcome: SearchOutcome) {
        titles = outcome.titles
        servedBy = outcome.servedBy
        fallback = outcome.fallback
        hasMore = outcome.hasMore
    }

    private fun loadTagLists() {
        if (tags !is Loadable.Ready) {
            tags = Loadable.Loading
            scope.launch {
                tags =
                    try {
                        Loadable.Ready(comick.tags())
                    } catch (e: ApiError) {
                        Loadable.Failed(e.message.orEmpty())
                    }
            }
        }
        if (genres !is Loadable.Ready) {
            genres = Loadable.Loading
            scope.launch {
                genres =
                    try {
                        Loadable.Ready(comick.genres())
                    } catch (e: ApiError) {
                        Loadable.Failed(e.message.orEmpty())
                    }
            }
        }
    }

    private companion object {
        const val RANDOM_PAGES = 25
        const val RANDOM_PAGE_SIZE = 20
        const val RANDOM_TRIES = 6
    }
}
