package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.mangadex.MangaDexOrder
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/** The MangaDex page's tabs: four catalog lists and the signed-in follow feed. */
enum class MangaDexTab(val label: String) {
    POPULAR_NEW("Popular new"),
    LATEST("Latest updates"),
    RECENT("Recently added"),
    TOP_RATED("Top rated"),
    FEED("Feed"),
    SEARCH("Search"),
}

/** One title in the feed with its new chapters, newest first. */
data class FeedGroup(val title: Title, val chapters: List<Chapter>)

/**
 * The MangaDex discover page under Browse. Catalog tabs page through MangaDex's catalog; Feed shows
 * new chapters of the titles the signed-in account follows, grouped by title.
 */
class MangaDexModel(
    private val scope: CoroutineScope,
    private val api: MangaDexApi,
    private val account: MangaDexAccount,
    private val languages: () -> List<String> = { listOf("en") },
    private val now: () -> Instant = Instant::now,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var tab by mutableStateOf(MangaDexTab.POPULAR_NEW)
        private set

    /** The current catalog tab's titles so far. */
    val titles = mutableStateListOf<Title>()
    var status by mutableStateOf<Loadable<Unit>>(Loadable.Loading)
        private set

    private var hasMore = true
    private var loading: Job? = null
    private var typing: Job? = null

    /** What the Search tab looks for. */
    var query by mutableStateOf("")
        private set

    /** Why the next page didn't load, while earlier pages stay shown; null when fine. */
    var pageError by mutableStateOf<String?>(null)
        private set

    /** True while a chapter's list is loading to open it in the reader. */
    var opening by mutableStateOf(false)
        private set

    /** The feed, grouped by title in the order their newest chapter came out. */
    val feed = mutableStateListOf<FeedGroup>()
    var feedStatus by mutableStateOf<Loadable<Unit>>(Loadable.Loading)
        private set

    private var feedOffset = 0
    private var feedHasMore = true
    private val feedItems = mutableListOf<Pair<String, Chapter>>()
    private val feedTitles = mutableMapOf<String, Title>()

    val signedIn: Boolean
        get() = account.connected

    private var started = false

    /**
     * Called each time the page opens. The first time it loads the tab; later it loads the Feed if
     * it couldn't before (the user has just signed in).
     */
    fun start() {
        if (!started) {
            started = true
            reload()
        } else if (
            tab == MangaDexTab.FEED &&
                feed.isEmpty() &&
                feedStatus !is Loadable.Ready &&
                loading?.isActive != true
        ) {
            reload()
        }
    }

    /** Typing in the Search tab: searches once the user pauses. */
    fun search(text: String) {
        query = text
        typing?.cancel()
        typing = scope.launch {
            kotlinx.coroutines.delay(SEARCH_DELAY)
            if (tab == MangaDexTab.SEARCH) reload()
        }
    }

    /** Tries the page that failed again. */
    fun retry() {
        pageError = null
        loadMore()
    }

    /** When the tab last changed (ms), so new results wait for the tab line to arrive. */
    private var switchedAt = 0L

    fun select(next: MangaDexTab) {
        if (next == tab) return
        tab = next
        switchedAt = clock()
        reload()
    }

    /** Loads the current tab again from the start. */
    fun reload() {
        loading?.cancel()
        pageError = null
        if (tab == MangaDexTab.FEED) {
            feed.clear()
            feedItems.clear()
            feedOffset = 0
            feedHasMore = true
            feedStatus = Loadable.Loading
            if (!account.connected) return
        } else {
            titles.clear()
            hasMore = true
            status = Loadable.Loading
        }
        loadMore()
    }

    /** The next page of the current tab, when there is one and nothing is loading. */
    fun loadMore() {
        if (loading?.isActive == true) return
        if (tab == MangaDexTab.FEED) loadFeed() else loadCatalog(tab)
    }

    private fun loadCatalog(which: MangaDexTab) {
        if (!hasMore) return
        if (which == MangaDexTab.SEARCH && query.isBlank()) {
            hasMore = false
            status = Loadable.Ready(Unit)
            return
        }
        loading = scope.launch {
            try {
                val page =
                    api.search(
                        query = if (which == MangaDexTab.SEARCH) query.trim() else null,
                        offset = titles.size,
                        limit = PAGE,
                        order =
                            when (which) {
                                MangaDexTab.LATEST -> MangaDexOrder.LATEST_UPLOAD
                                MangaDexTab.RECENT -> MangaDexOrder.NEWEST
                                MangaDexTab.TOP_RATED -> MangaDexOrder.RATING
                                MangaDexTab.SEARCH -> MangaDexOrder.RELEVANCE
                                else -> MangaDexOrder.MOST_FOLLOWED
                            },
                        translatedLanguage = null,
                        translatedLanguages = languages(),
                        createdAtSince =
                            if (which == MangaDexTab.POPULAR_NEW) since(NEW_DAYS) else null,
                    )
                if (tab != which) return@launch
                // A screenful of covers is heavy to draw: let the tab line finish moving first.
                val wait = switchedAt + SETTLE_MILLIS - clock()
                if (wait > 0) kotlinx.coroutines.delay(wait)
                if (tab != which) return@launch
                val seen = titles.mapTo(HashSet()) { it.id }
                titles += page.items.filter { seen.add(it.id) }
                hasMore = page.hasMore && page.items.isNotEmpty()
                status = Loadable.Ready(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (tab != which) return@launch
                if (titles.isEmpty()) status = Loadable.Failed(message(e))
                else pageError = message(e)
            }
        }
    }

    private fun loadFeed() {
        if (!feedHasMore || !account.connected) return
        loading = scope.launch {
            try {
                val page = account.feed(offset = feedOffset, limit = FEED_PAGE, languages())
                feedOffset += page.items.size
                feedHasMore = page.hasMore && page.items.isNotEmpty()
                val missing = page.items.map { it.mangaId }.filter { it !in feedTitles }
                api.titles(missing).forEach { feedTitles[it.id] = it }
                feedItems += page.items.map { it.mangaId to it.chapter }
                feed.clear()
                feed += group(feedItems, feedTitles)
                feedStatus = Loadable.Ready(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.Unauthorized) {
                feedStatus =
                    Loadable.Failed("MangaDex signed you out. Sign in again to see your feed.")
            } catch (e: Exception) {
                if (feed.isEmpty()) feedStatus = Loadable.Failed(message(e))
                else pageError = message(e)
            }
        }
    }

    /**
     * Opens [chapter] of [title] in the reader: loads the title's chapters so next and previous
     * work, then hands them over in reading order.
     */
    fun read(title: Title, chapter: Chapter, open: (Page.Reader) -> Unit, failed: (Title) -> Unit) {
        if (opening) return
        // Chapters a publisher hosts (MangaPlus and others) have no pages here: the title page
        // offers them in the browser.
        if (chapter.externalUrl != null) {
            failed(title)
            return
        }
        opening = true
        scope.launch {
            val chapters =
                try {
                    api.allChapters(title.id, languages()).reversed()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            opening = false
            val index = chapters.indexOfFirst { it.id == chapter.id && it.externalUrl == null }
            if (index >= 0) open(Page.Reader(title, chapters, index)) else failed(title)
        }
    }

    private fun since(days: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC)
            .format(now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS))

    private fun message(e: Exception): String =
        when (e) {
            is ApiError.Unreachable ->
                "Couldn’t reach MangaDex. Check your connection and try again."
            is ApiError.RateLimited -> "MangaDex asked Beako to slow down. Try again in a minute."
            else -> "MangaDex couldn’t load this list. Try again."
        }

    companion object {
        private const val PAGE = 30
        private const val FEED_PAGE = 60
        private const val NEW_DAYS = 30L
        private const val SEARCH_DELAY = 400L
        private const val SETTLE_MILLIS = 320L

        /** Chapters grouped under their title, titles ordered by their newest chapter. */
        internal fun group(
            items: List<Pair<String, Chapter>>,
            titles: Map<String, Title>,
        ): List<FeedGroup> =
            items.groupBy({ it.first }, { it.second }).mapNotNull { (id, chapters) ->
                titles[id]?.let { FeedGroup(it, chapters) }
            }
    }
}
