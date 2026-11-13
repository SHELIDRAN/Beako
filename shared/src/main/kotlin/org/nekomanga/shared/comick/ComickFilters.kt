package org.nekomanga.shared.comick

import org.nekomanga.shared.model.ComicType

/** Sort orders of Comick's search, named as Beako's Search screen shows them. */
enum class ComickSort(internal val key: String) {
    /** Newest additions to the catalog. */
    LATEST("created_at"),
    /** Most followed. */
    POPULAR("follow"),
    /** Weighted score: more votes, more trust. */
    BAYESIAN_RATING("rating"),
    AVERAGE_RATING("average_rating"),
    /** Most recently uploaded chapter. */
    LAST_UPDATED("uploaded"),
    /** Most follows gained, used with [ComickFilters.addedWithinDays] for "new and followed". */
    NEW_FOLLOWS("user_follow_count"),
}

enum class Demographic(internal val key: Int, val label: String) {
    SHOUNEN(1, "Shounen"),
    SHOUJO(2, "Shoujo"),
    SEINEN(3, "Seinen"),
    JOSEI(4, "Josei"),
    NONE(5, "None"),
}

/**
 * Everything Comick's catalog search can filter on. Tag and genre values are slugs from
 * [ComickApi.tags] and [ComickApi.genres]. The API's own limits are enforced by [toQuery]: up to 10
 * included and 40 excluded genres, 10 included and 30 excluded tags.
 */
data class ComickFilters(
    val query: String = "",
    val includedGenres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val includedTags: List<String> = emptyList(),
    val excludedTags: List<String> = emptyList(),
    val demographics: Set<Demographic> = emptySet(),
    val types: Set<ComicType> = emptySet(),
    val contentRatings: Set<String> = setOf("safe"),
    /** Series status: 1 ongoing, 2 completed, 3 cancelled, 4 hiatus. */
    val status: Int? = null,
    val translationCompleted: Boolean = false,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val minChapters: Int? = null,
    val maxChapters: Int? = null,
    val minRating: Double? = null,
    /** Only titles added to Comick in the last N days. */
    val addedWithinDays: Int? = null,
    val includeWithoutChapters: Boolean = false,
    val sort: ComickSort = ComickSort.LATEST,
    val page: Int = 1,
    val limit: Int = 30,
) {
    val hasKeyword: Boolean
        get() = query.isNotBlank()

    /** True when a filter is set that only Comick understands, so MangaDex can't stand in. */
    val usesComickOnlyFilters: Boolean
        get() =
            includedGenres.isNotEmpty() ||
                excludedGenres.isNotEmpty() ||
                includedTags.isNotEmpty() ||
                excludedTags.isNotEmpty()

    /**
     * True when "Others" is among the chosen types. Comick has no country code for it, so the
     * country filter is left off the request and [matchesTypes] is applied to the results.
     */
    val filtersTypesLocally: Boolean
        get() = ComicType.OTHER in types

    /** How a tag or genre is used in the filter. */
    enum class Use {
        NONE,
        INCLUDED,
        EXCLUDED,
    }

    fun tagUse(slug: String): Use = useOf(slug, includedTags, excludedTags)

    fun genreUse(slug: String): Use = useOf(slug, includedGenres, excludedGenres)

    /**
     * The next state of a tag when it is clicked: include, then exclude, then clear. A step that
     * would pass the API's limit is skipped, so an 11th tag goes straight to excluded.
     */
    fun cycleTag(slug: String): ComickFilters {
        val (included, excluded) =
            cycle(slug, includedTags, excludedTags, MAX_INCLUDED, MAX_EXCLUDED_TAGS)
        return copy(includedTags = included, excludedTags = excluded)
    }

    fun cycleGenre(slug: String): ComickFilters {
        val (included, excluded) =
            cycle(slug, includedGenres, excludedGenres, MAX_INCLUDED, MAX_EXCLUDED_GENRES)
        return copy(includedGenres = included, excludedGenres = excluded)
    }

    /** How many filters differ from the defaults, for the "Filters · N" label. */
    val activeCount: Int
        get() =
            includedGenres.size +
                excludedGenres.size +
                includedTags.size +
                excludedTags.size +
                demographics.size +
                types.size +
                listOfNotNull(
                        status,
                        fromYear,
                        toYear,
                        minChapters,
                        maxChapters,
                        minRating,
                        addedWithinDays,
                    )
                    .size +
                (if (translationCompleted) 1 else 0) +
                (if (includeWithoutChapters) 1 else 0)

    /** The same search with every filter cleared, keeping the keyword and sort. */
    fun cleared(): ComickFilters =
        ComickFilters(query = query, sort = sort, contentRatings = contentRatings, limit = limit)

    fun matchesTypes(type: ComicType?): Boolean =
        types.isEmpty() || (type ?: ComicType.OTHER) in types

    fun toQuery(): List<Pair<String, String>> = buildList {
        if (hasKeyword) add("q" to query.trim())
        includedGenres.take(MAX_INCLUDED).forEach { add("genres" to it) }
        excludedGenres.take(MAX_EXCLUDED_GENRES).forEach { add("excludes" to it) }
        includedTags.take(MAX_INCLUDED).forEach { add("tags" to it) }
        excludedTags.take(MAX_EXCLUDED_TAGS).forEach { add("excluded-tags" to it) }
        demographics.sortedBy { it.key }.forEach { add("demographic" to it.key.toString()) }
        if (!filtersTypesLocally) {
            types
                .sortedBy { it.ordinal }
                .forEach { type ->
                    when (type) {
                        ComicType.MANGA -> add("country" to "jp")
                        ComicType.MANHWA -> add("country" to "kr")
                        ComicType.MANHUA -> add("country" to "cn")
                        ComicType.OTHER -> Unit
                    }
                }
        }
        contentRatings.sorted().forEach { add("content_rating" to it) }
        status?.let { add("status" to it.toString()) }
        if (translationCompleted) add("completed" to "true")
        fromYear?.let { add("from" to it.toString()) }
        toYear?.let { add("to" to it.toString()) }
        minChapters?.let { add("minimum" to it.toString()) }
        maxChapters?.let { add("maximum" to it.toString()) }
        minRating?.let { add("minimum_rating" to it.toString()) }
        addedWithinDays?.let { add("time" to it.toString()) }
        if (includeWithoutChapters) add("showall" to "true")
        add("sort" to sort.key)
        add("page" to page.coerceIn(1, MAX_PAGE).toString())
        add("limit" to limit.coerceIn(1, MAX_LIMIT).toString())
        // Adds cover_url to each result.
        add("tachiyomi" to "true")
    }

    companion object {
        private fun useOf(slug: String, included: List<String>, excluded: List<String>): Use =
            when (slug) {
                in included -> Use.INCLUDED
                in excluded -> Use.EXCLUDED
                else -> Use.NONE
            }

        private fun cycle(
            slug: String,
            included: List<String>,
            excluded: List<String>,
            maxIncluded: Int,
            maxExcluded: Int,
        ): Pair<List<String>, List<String>> =
            when {
                slug in included ->
                    if (excluded.size < maxExcluded) (included - slug) to (excluded + slug)
                    else (included - slug) to excluded
                slug in excluded -> included to (excluded - slug)
                included.size < maxIncluded -> (included + slug) to excluded
                excluded.size < maxExcluded -> included to (excluded + slug)
                else -> included to excluded
            }

        const val MAX_INCLUDED = 10
        const val MAX_EXCLUDED_GENRES = 40
        const val MAX_EXCLUDED_TAGS = 30
        const val MAX_PAGE = 50
        const val MAX_LIMIT = 50
    }
}
