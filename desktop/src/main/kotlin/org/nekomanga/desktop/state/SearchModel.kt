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
) {
    var filters by mutableStateOf(ComickFilters(sort = ComickSort.LATEST))
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

    /** True while the user has chosen to search MangaDex directly. */
    var mangaDexByHand by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var started = false

    fun start(query: String = "") {
        if (started) return
        started = true
        filters = filters.copy(query = query)
        run(immediately = true)
        loadTagLists()
    }

    fun update(change: (ComickFilters) -> ComickFilters) {
        filters = change(filters).copy(page = 1)
        run(immediately = false)
    }

    fun retryComick() {
        mangaDexByHand = false
        loadTagLists()
        run(immediately = true)
    }

    fun searchMangaDex() {
        mangaDexByHand = true
        run(immediately = true)
    }

    fun loadMore() {
        if (!hasMore || loadingMore || status !is Loadable.Ready) return
        loadingMore = true
        val next = filters.copy(page = filters.page + 1)
        scope.launch {
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
}
