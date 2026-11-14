package org.nekomanga.shared.model

import kotlinx.serialization.Serializable

/** Where a title or chapter came from. Extension sources add their own ids later. */
object Sources {
    const val COMICK = "comick"
    const val MANGADEX = "mangadex"
}

/** IDs on other trackers, used to recognise the same title across services. */
@Serializable
data class ExternalIds(
    val anilist: String? = null,
    val myAnimeList: String? = null,
    val mangaUpdates: String? = null,
    val mangaBaka: String? = null,
) {
    /** True when both sides know at least one tracker ID and it is the same. */
    fun sharesIdWith(other: ExternalIds): Boolean =
        sameId(anilist, other.anilist) ||
            sameId(myAnimeList, other.myAnimeList) ||
            sameId(mangaUpdates, other.mangaUpdates) ||
            sameId(mangaBaka, other.mangaBaka)

    private fun sameId(a: String?, b: String?) = !a.isNullOrBlank() && a == b
}

@Serializable
enum class ComicType(val label: String) {
    MANGA("Manga"),
    MANHWA("Manhwa"),
    MANHUA("Manhua"),
    OTHER("Other");

    companion object {
        /** From an origin country (jp, kr, cn…) or original language (ja, ko, zh…). */
        fun fromOrigin(code: String?): ComicType? =
            when (code?.lowercase()) {
                null,
                "" -> null
                "jp",
                "ja" -> MANGA
                "kr",
                "ko" -> MANHWA
                "cn",
                "hk",
                "tw",
                "zh",
                "zh-hk" -> MANHUA
                else -> OTHER
            }
    }
}

@Serializable
enum class PublicationStatus(val label: String) {
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    HIATUS("Hiatus"),
}

@Serializable
data class Title(
    val source: String,
    /** The source's own id: a Comick HID or a MangaDex UUID. */
    val id: String,
    val title: String,
    /** Comick's URL name, when the source has one. */
    val slug: String? = null,
    val altTitles: List<String> = emptyList(),
    val description: String = "",
    val coverUrl: String? = null,
    val year: Int? = null,
    val status: PublicationStatus? = null,
    val type: ComicType? = null,
    /** Origin country code as the source gave it (jp, kr, cn…), for the flag badge. */
    val country: String? = null,
    val contentRating: String? = null,
    val demographic: String? = null,
    /** Score out of 10. */
    val rating: Double? = null,
    val follows: Int? = null,
    val chapterCount: Int? = null,
    /** ISO-8601 time of the latest upload, when known. */
    val lastUploadedAt: String? = null,
    val tags: List<String> = emptyList(),
    val externalIds: ExternalIds = ExternalIds(),
)

@Serializable
data class Chapter(
    val source: String,
    val id: String,
    /** Chapter number as text, so "12.5" and unnumbered chapters survive. */
    val number: String?,
    val volume: String? = null,
    val title: String? = null,
    val language: String,
    val group: String? = null,
    /** ISO-8601 publish time. */
    val publishedAt: String,
    val pages: Int = 0,
    /** Set when the chapter is hosted by a publisher and opens in the browser. */
    val externalUrl: String? = null,
)

/** The image URLs of one chapter, in reading order. */
data class PageList(val urls: List<String>)

/** One page of results plus whether more can be requested. */
data class Paged<T>(val items: List<T>, val hasMore: Boolean)
