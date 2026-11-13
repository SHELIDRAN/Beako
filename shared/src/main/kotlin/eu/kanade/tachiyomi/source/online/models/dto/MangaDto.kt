// Copied from Neko (app/src/main/java/eu/kanade/tachiyomi/source/online/models/dto) for the
// Android-free shared module. Changes: TagDto gained optional attributes; asMdMap uses mdJson.
package eu.kanade.tachiyomi.source.online.models.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class MangaListDto(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val result: String,
    val data: List<MangaDataDto>,
)

@Serializable data class MangaDto(val result: String, val data: MangaDataDto)

@Serializable
data class MangaDataDto(
    val id: String,
    val type: String,
    val attributes: MangaAttributesDto,
    val relationships: List<RelationshipDto>,
)

@Serializable
data class MangaAttributesDto(
    val title: Map<String, String?>,
    val altTitles: List<JsonElement>?,
    val description: JsonElement,
    val links: JsonElement?,
    val originalLanguage: String,
    val lastVolume: String?,
    val lastChapter: String?,
    val contentRating: String?,
    val publicationDemographic: String?,
    val status: String?,
    val year: Int?,
    val tags: List<TagDto>,
)

@Serializable data class TagDto(val id: String, val attributes: TagAttributesDto? = null)

// Added for Beako: tag names and groups, which the desktop app shows.
@Serializable
data class TagAttributesDto(val name: Map<String, String?> = emptyMap(), val group: String? = null)

@Serializable
data class RelationshipDtoList(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val result: String,
    val data: List<RelationshipDto>,
)

@Serializable
data class RelationshipDto(
    val id: String,
    val type: String,
    val attributes: IncludesAttributesDto? = null,
)

@Serializable
data class MangaRelationshipDto(val id: String, val attributes: MangaAttributesDto? = null)

@Serializable
data class IncludesAttributesDto(
    val name: String? = null,
    val username: String? = null,
    val fileName: String? = null,
    val description: String? = null,
    val locale: String? = null,
    val volume: String? = null,
)

@Serializable
data class AuthorListDto(
    val result: String,
    val data: List<AuthorDataDto>,
    val limit: Int,
    val offset: Int,
    val total: Int,
)

@Serializable data class AuthorDto(val result: String, val data: AuthorDataDto)

@Serializable
data class AuthorDataDto(
    val id: String,
    val attributes: AuthorAttributesDto,
    val relationships: List<MangaRelationshipDto>? = null,
)

@Serializable data class AuthorAttributesDto(val name: String, val biography: JsonElement)

@Serializable data class ReadingStatusDto(val status: String?)

@Serializable data class ReadingStatusMapDto(val statuses: Map<String, String?>)

@Serializable data class ReadChapterDto(val data: List<String>)

@Serializable data class CoverListDto(val results: List<CoverDto>)

@Serializable data class CoverDto(val data: CoverDataDto)

@Serializable
data class CoverDataDto(
    val attributes: CoverAttributesDto,
    val relationships: List<RelationshipDto>,
)

@Serializable data class CoverAttributesDto(val fileName: String)

@Serializable data class AggregateDto(val result: String, val volumes: JsonElement)

@Serializable
data class AggregateVolume(
    val volume: String,
    val count: String,
    val chapters: Map<String, AggregateChapter>,
)

@Serializable
data class AggregateChapter(
    val chapter: String,
    val id: String,
    val others: List<String>,
    val count: String,
)

/** Lenient parser for MangaDex payloads (Neko keeps this in MdUtil.jsonParser). */
val mdJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

inline fun <reified T> JsonElement.asMdMap(): Map<String, T> {
    return runCatching { mdJson.decodeFromJsonElement<Map<String, T>>(jsonObject) }
        .getOrElse { emptyMap() }
}
