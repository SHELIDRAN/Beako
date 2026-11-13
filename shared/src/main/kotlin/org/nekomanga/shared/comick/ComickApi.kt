package org.nekomanga.shared.comick

import kotlinx.serialization.json.JsonElement
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.ExternalIds
import org.nekomanga.shared.model.PublicationStatus
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.RateLimiter
import org.nekomanga.shared.net.bool
import org.nekomanga.shared.net.buildUrl
import org.nekomanga.shared.net.double
import org.nekomanga.shared.net.get
import org.nekomanga.shared.net.getJson
import org.nekomanga.shared.net.int
import org.nekomanga.shared.net.items
import org.nekomanga.shared.net.str

/** A genre, theme, format or content descriptor from Comick's genre list. */
data class ComickGenre(val slug: String, val name: String, val group: String, val count: Int?)

/** One of Comick's community tags, with how many titles carry it. */
data class ComickTag(val slug: String, val name: String, val count: Int)

/** The charts on Comick's home page. */
enum class ComickChart {
    /** New titles with the most follows in the period. */
    MOST_FOLLOWED_NEW,
    /** Titles with the most recent attention. */
    MOST_RECENT_POPULAR,
    TRENDING,
}

/**
 * Comick's catalog API, used for discovery only: search, tags, genres and charts. No login is
 * needed. Beako never reads chapters or page images from Comick.
 *
 * Stays well under the catalog limit of 200 requests a minute per IP address.
 */
class ComickApi(
    private val http: HttpFetcher,
    private val limiter: RateLimiter = RateLimiter(permits = 120, periodMillis = 60_000),
    private val baseUrl: String = "https://api.comick.dev",
) {
    suspend fun search(filters: ComickFilters): List<Title> {
        val results = get("v1.0/search/", filters.toQuery()).items().map(::parseTitle)
        return if (filters.filtersTypesLocally) results.filter { filters.matchesTypes(it.type) }
        else results
    }

    /** A home-page chart for the last [days] days (7, 30 or 90). */
    suspend fun chart(
        chart: ComickChart,
        days: Int = 7,
        contentRatings: Set<String> = setOf("safe"),
        limit: Int = 20,
    ): List<Title> =
        when (chart) {
            // Comick's own "top follows" list has no covers; the same ranking comes from search,
            // sorted by follows among titles added in the period (as the Rust prototype found).
            ComickChart.MOST_FOLLOWED_NEW ->
                search(
                    ComickFilters(
                        sort = ComickSort.NEW_FOLLOWS,
                        addedWithinDays = days,
                        contentRatings = contentRatings,
                        limit = limit,
                    ),
                )
            ComickChart.MOST_RECENT_POPULAR,
            ComickChart.TRENDING -> {
                val params = buildList {
                    add("day" to days.toString())
                    if ("erotica" in contentRatings) add("accept_mature_content" to "true")
                }
                val body = get("top", params)
                val wanted =
                    if (chart == ComickChart.MOST_RECENT_POPULAR) body["recentRank"]
                    else body["trending"][days.toString()]
                wanted
                    .items()
                    .ifEmpty { body["rank"].items() }
                    .map(::parseTitle)
                    .filter { it.coverUrl != null }
                    .take(limit)
            }
        }

    suspend fun genres(): List<ComickGenre> =
        get("genre/", emptyList()).items().mapNotNull { g ->
            ComickGenre(
                slug = g.str("slug") ?: return@mapNotNull null,
                name = g.str("name") ?: return@mapNotNull null,
                group = g.str("group").orEmpty().lowercase(),
                count = g.int("comic_count"),
            )
        }

    /** Every community tag with at least one title, most used first. */
    suspend fun tags(): List<ComickTag> = parseTags(get("category/", emptyList()))

    /** The 300 most-used community tags. */
    suspend fun popularTags(): List<ComickTag> = parseTags(get("category/popular", emptyList()))

    private suspend fun get(path: String, params: List<Pair<String, String>>): JsonElement {
        limiter.acquire()
        return http.getJson(SERVICE, buildUrl(baseUrl, path, params))
    }

    companion object {
        const val SERVICE = "Comick"
        const val COVERS = "https://meo.comick.pictures"

        internal fun parseTags(body: JsonElement): List<ComickTag> =
            body
                .items()
                .mapNotNull { t ->
                    val count = t.int("comic_count") ?: return@mapNotNull null
                    val slug = t.str("slug") ?: return@mapNotNull null
                    if (count <= 0) null else ComickTag(slug, t.str("title") ?: slug, count)
                }
                .sortedByDescending { it.count }

        /** The medium-size cover (about 190 KB instead of the 1–2 MB original). */
        private fun coverOf(v: JsonElement): String? {
            v.str("cover_url")?.let {
                return it
            }
            val key = v["md_covers"].items().firstOrNull().str("b2key") ?: return null
            val dot = key.lastIndexOf('.')
            return if (dot > 0) "$COVERS/${key.substring(0, dot)}-m${key.substring(dot)}"
            else "$COVERS/$key"
        }

        /**
         * A Comick catalog entry as a [Title]. Reads leniently, because search, chart and detail
         * responses carry different subsets of fields.
         */
        internal fun parseTitle(v: JsonElement): Title {
            val ext = v["external_ids"]
            val romanized = v.str("title").orEmpty()
            val others = v["md_titles"].items()
            val english = others.firstOrNull { it.str("lang") == "en" }.str("title")
            // Prefer an English title when the main one is romanized.
            val main =
                if (v.bool("is_english_title") == true || english == null) romanized else english
            val alt =
                (listOf(romanized) + others.mapNotNull { it.str("title") })
                    .filter { it.isNotBlank() && it != main }
                    .distinct()
            val country = v.str("country")
            return Title(
                source = Sources.COMICK,
                id = v.str("hid").orEmpty(),
                slug = v.str("slug"),
                title = main,
                altTitles = alt,
                description = v.str("desc").orEmpty(),
                coverUrl = coverOf(v),
                year = v.int("year"),
                status =
                    when (v.int("status")) {
                        1 -> PublicationStatus.ONGOING
                        2 -> PublicationStatus.COMPLETED
                        3 -> PublicationStatus.CANCELLED
                        4 -> PublicationStatus.HIATUS
                        else -> null
                    },
                type = ComicType.fromOrigin(country),
                country = country,
                contentRating = v.str("content_rating"),
                demographic =
                    when (v.int("demographic")) {
                        1 -> "shounen"
                        2 -> "shoujo"
                        3 -> "seinen"
                        4 -> "josei"
                        else -> null
                    },
                rating = v.double("bayesian_rating") ?: v.double("rating"),
                follows = v.int("user_follow_count") ?: v.int("follow_count"),
                chapterCount = v.int("last_chapter") ?: v.int("chapter_count"),
                lastUploadedAt = v.str("uploaded_at"),
                externalIds =
                    ExternalIds(
                        anilist = ext.str("anilist"),
                        myAnimeList = ext.str("myanimelist"),
                        mangaUpdates = ext.str("mangaupdates"),
                        mangaBaka = ext.str("mangabaka"),
                    ),
            )
        }
    }
}
