package org.nekomanga.shared.mangadex

import eu.kanade.tachiyomi.source.online.models.dto.AtHomeDto
import eu.kanade.tachiyomi.source.online.models.dto.ChapterDataDto
import eu.kanade.tachiyomi.source.online.models.dto.ChapterListDto
import eu.kanade.tachiyomi.source.online.models.dto.MangaDataDto
import eu.kanade.tachiyomi.source.online.models.dto.MangaDto
import eu.kanade.tachiyomi.source.online.models.dto.MangaListDto
import eu.kanade.tachiyomi.source.online.models.dto.StatisticResponseDto
import eu.kanade.tachiyomi.source.online.models.dto.asMdMap
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.ExternalIds
import org.nekomanga.shared.model.PageList
import org.nekomanga.shared.model.Paged
import org.nekomanga.shared.model.PublicationStatus
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.BeakoJson
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.RateLimiter
import org.nekomanga.shared.net.buildUrl
import org.nekomanga.shared.net.getJson

enum class MangaDexOrder(internal val key: String) {
    RELEVANCE("relevance"),
    MOST_FOLLOWED("followedCount"),
    LATEST_UPLOAD("latestUploadedChapter"),
    NEWEST("createdAt"),
    RATING("rating"),
}

/** Follows and score for one title, from MangaDex's statistics endpoint. */
data class MangaDexStats(val follows: Int?, val rating: Double?)

/**
 * MangaDex's public API: Beako's fallback search and a built-in reading source. Stays under
 * MangaDex's limits of 5 requests a second overall and 40 a minute for page lists.
 */
class MangaDexApi(
    private val http: HttpFetcher,
    private val limiter: RateLimiter = RateLimiter(permits = 5, periodMillis = 1_000),
    private val pageListLimiter: RateLimiter = RateLimiter(permits = 40, periodMillis = 60_000),
    private val baseUrl: String = "https://api.mangadex.org",
) {
    /** Searches by title, or lists the catalog in [order] when [query] is blank. */
    suspend fun search(
        query: String?,
        offset: Int = 0,
        limit: Int = 30,
        order: MangaDexOrder = MangaDexOrder.RELEVANCE,
        contentRatings: Set<String> = DEFAULT_RATINGS,
        translatedLanguage: String? = "en",
    ): Paged<Title> {
        val params = buildList {
            if (!query.isNullOrBlank()) add("title" to query.trim())
            add("limit" to limit.toString())
            add("offset" to offset.toString())
            add("order[${order.key}]" to "desc")
            add("includes[]" to "cover_art")
            contentRatings.forEach { add("contentRating[]" to it) }
            // Only titles that have chapters in the reader's language.
            translatedLanguage?.let {
                add("availableTranslatedLanguage[]" to it)
                add("hasAvailableChapters" to "true")
            }
        }
        val page = get("manga", params, MangaListDto.serializer())
        return Paged(page.data.map(::toTitle), page.offset + page.data.size < page.total)
    }

    suspend fun title(id: String): Title {
        val params = listOf("includes[]" to "cover_art")
        return toTitle(get("manga/$id", params, MangaDto.serializer()).data)
    }

    /** One page of a title's chapters, newest first. */
    suspend fun chapters(
        mangaId: String,
        languages: List<String> = listOf("en"),
        offset: Int = 0,
        limit: Int = FEED_PAGE,
        contentRatings: Set<String> = ALL_RATINGS,
    ): Paged<Chapter> {
        val params = buildList {
            add("limit" to limit.toString())
            add("offset" to offset.toString())
            add("order[chapter]" to "desc")
            add("order[volume]" to "desc")
            add("includes[]" to "scanlation_group")
            languages.forEach { add("translatedLanguage[]" to it) }
            contentRatings.forEach { add("contentRating[]" to it) }
        }
        val page = get("manga/$mangaId/feed", params, ChapterListDto.serializer())
        return Paged(page.data.map(::toChapter), page.offset + page.data.size < page.total)
    }

    /** Every chapter of a title, newest first. */
    suspend fun allChapters(
        mangaId: String,
        languages: List<String> = listOf("en"),
    ): List<Chapter> {
        val all = mutableListOf<Chapter>()
        while (true) {
            val page = chapters(mangaId, languages, offset = all.size)
            all += page.items
            if (!page.hasMore || page.items.isEmpty()) return all
        }
    }

    /** The image URLs of a chapter. [dataSaver] picks MangaDex's smaller, compressed images. */
    suspend fun pages(chapterId: String, dataSaver: Boolean = false): PageList {
        pageListLimiter.acquire()
        val server = get("at-home/server/$chapterId", emptyList(), AtHomeDto.serializer())
        val (quality, files) =
            if (dataSaver) "data-saver" to server.chapter.dataSaver
            else "data" to server.chapter.data
        return PageList(files.map { "${server.baseUrl}/$quality/${server.chapter.hash}/$it" })
    }

    suspend fun statistics(mangaIds: List<String>): Map<String, MangaDexStats> {
        if (mangaIds.isEmpty()) return emptyMap()
        val params = mangaIds.map { "manga[]" to it }
        val response = get("statistics/manga", params, StatisticResponseDto.serializer())
        return response.statistics.mapValues { (_, s) ->
            MangaDexStats(s.follows, s.rating?.bayesian ?: s.rating?.average)
        }
    }

    private suspend fun <T> get(
        path: String,
        params: List<Pair<String, String>>,
        serializer: DeserializationStrategy<T>,
    ): T {
        limiter.acquire()
        val json = http.getJson(SERVICE, buildUrl(baseUrl, path, params))
        return try {
            BeakoJson.decodeFromJsonElement(serializer, json)
        } catch (e: SerializationException) {
            throw ApiError.Unreadable(SERVICE, e)
        }
    }

    companion object {
        const val SERVICE = "MangaDex"
        const val COVERS = "https://uploads.mangadex.org/covers"
        private const val FEED_PAGE = 500
        val DEFAULT_RATINGS = setOf("safe", "suggestive")
        val ALL_RATINGS = setOf("safe", "suggestive", "erotica", "pornographic")

        internal fun toTitle(data: MangaDataDto): Title {
            val a = data.attributes
            val english = a.title["en"]
            val main = english ?: a.title.values.firstOrNull { !it.isNullOrBlank() } ?: ""
            val alt =
                a.altTitles
                    .orEmpty()
                    .flatMap { (it as? JsonObject)?.values.orEmpty() }
                    .mapNotNull {
                        (it as? JsonPrimitive)?.content?.takeIf { text -> text.isNotBlank() }
                    }
            val links = a.links?.asMdMap<String>().orEmpty()
            val cover =
                data.relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
            return Title(
                source = Sources.MANGADEX,
                id = data.id,
                title = main,
                altTitles = (a.title.values.filterNotNull() + alt).filter { it != main }.distinct(),
                description = a.description.asMdMap<String>()["en"].orEmpty(),
                coverUrl = cover?.let { "$COVERS/${data.id}/$it.512.jpg" },
                year = a.year,
                status =
                    when (a.status) {
                        "ongoing" -> PublicationStatus.ONGOING
                        "completed" -> PublicationStatus.COMPLETED
                        "cancelled" -> PublicationStatus.CANCELLED
                        "hiatus" -> PublicationStatus.HIATUS
                        else -> null
                    },
                type = ComicType.fromOrigin(a.originalLanguage),
                country =
                    when (ComicType.fromOrigin(a.originalLanguage)) {
                        ComicType.MANGA -> "jp"
                        ComicType.MANHWA -> "kr"
                        ComicType.MANHUA -> "cn"
                        else -> null
                    },
                contentRating = a.contentRating,
                demographic = a.publicationDemographic,
                tags = a.tags.mapNotNull { it.attributes?.name?.get("en") },
                externalIds =
                    ExternalIds(
                        anilist = links["al"],
                        myAnimeList = links["mal"],
                        mangaUpdates = links["mu"],
                    ),
            )
        }

        internal fun toChapter(data: ChapterDataDto): Chapter {
            val a = data.attributes
            return Chapter(
                source = Sources.MANGADEX,
                id = data.id,
                number = a.chapter,
                volume = a.volume,
                title = a.title?.takeIf { it.isNotBlank() },
                language = a.translatedLanguage,
                group =
                    data.relationships
                        .filter { it.type == "scanlation_group" }
                        .mapNotNull { it.attributes?.name }
                        .joinToString(" & ")
                        .takeIf { it.isNotEmpty() },
                publishedAt = a.readableAt,
                pages = a.pages,
                externalUrl = a.externalUrl,
            )
        }
    }
}
