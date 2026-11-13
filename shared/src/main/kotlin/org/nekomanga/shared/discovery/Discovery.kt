package org.nekomanga.shared.discovery

import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/** Why a search was answered by MangaDex instead of Comick. */
enum class FallbackReason {
    COMICK_UNAVAILABLE,
    COMICK_FOUND_NOTHING,
}

data class SearchOutcome(
    val titles: List<Title>,
    /** [Sources.COMICK] or [Sources.MANGADEX]. */
    val servedBy: String,
    /** Set when MangaDex stood in for Comick; the UI shows a banner with "Try Comick again". */
    val fallback: FallbackReason? = null,
    val hasMore: Boolean = false,
)

/**
 * Beako's search: Comick first, MangaDex as the fallback.
 *
 * MangaDex takes over a keyword search when Comick is unavailable (unreachable, a Cloudflare
 * challenge, a server error or a rate limit) or finds nothing. Browsing by Comick's tags or genres
 * never falls back, because MangaDex doesn't have them; there the error reaches the caller.
 */
class Discovery(private val comick: ComickApi, private val mangaDex: MangaDexApi) {

    suspend fun search(filters: ComickFilters): SearchOutcome {
        val canFallBack = filters.hasKeyword && !filters.usesComickOnlyFilters
        val fromComick =
            try {
                comick.search(filters)
            } catch (e: ApiError) {
                if (!canFallBack || !e.isOutage) throw e
                return searchMangaDex(filters, FallbackReason.COMICK_UNAVAILABLE)
            }
        if (fromComick.isEmpty() && canFallBack && filters.page == 1) {
            return searchMangaDex(filters, FallbackReason.COMICK_FOUND_NOTHING)
        }
        return SearchOutcome(
            titles = fromComick,
            servedBy = Sources.COMICK,
            hasMore = fromComick.size >= filters.limit && filters.page < ComickFilters.MAX_PAGE,
        )
    }

    /** A search the user sent to MangaDex by hand, or a fallback with its [reason]. */
    suspend fun searchMangaDex(
        filters: ComickFilters,
        reason: FallbackReason? = null,
    ): SearchOutcome {
        val page =
            mangaDex.search(
                query = filters.query,
                offset = (filters.page - 1) * filters.limit,
                limit = filters.limit,
                contentRatings = filters.contentRatings.ifEmpty { MangaDexApi.DEFAULT_RATINGS },
            )
        return SearchOutcome(page.items, Sources.MANGADEX, reason, page.hasMore)
    }

    /**
     * The MangaDex entry for a title from another service: by shared AniList, MyAnimeList or
     * MangaUpdates ID first, then by an exact title match. Null when MangaDex doesn't have it.
     */
    suspend fun findOnMangaDex(title: Title): Title? {
        if (title.source == Sources.MANGADEX) return title
        val names = (listOf(title.title) + title.altTitles).filter { it.isNotBlank() }.take(3)
        var byName: Title? = null
        for (name in names) {
            val candidates = mangaDex.search(name, limit = 10, translatedLanguage = null).items
            candidates
                .firstOrNull { it.externalIds.sharesIdWith(title.externalIds) }
                ?.let {
                    return it
                }
            if (byName == null) byName = candidates.firstOrNull { sameName(it, title) }
        }
        return byName
    }

    private fun sameName(a: Title, b: Title): Boolean {
        val left = (listOf(a.title) + a.altTitles).map(::normalise).toSet()
        return (listOf(b.title) + b.altTitles).map(::normalise).any {
            it.isNotEmpty() && it in left
        }
    }

    companion object {
        /** Lower case with punctuation and spacing removed, for comparing titles. */
        fun normalise(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }
    }
}
