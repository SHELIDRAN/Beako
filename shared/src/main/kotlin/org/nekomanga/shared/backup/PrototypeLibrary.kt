package org.nekomanga.shared.backup

import java.io.File
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.nekomanga.shared.library.LibraryData
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.library.Progress
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.PublicationStatus
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.BeakoJson
import org.nekomanga.shared.net.get
import org.nekomanga.shared.net.int
import org.nekomanga.shared.net.items
import org.nekomanga.shared.net.str

/**
 * The library of Neko Desktop, the earlier Rust prototype of this app (`rust/neko-desktop`): its
 * `library.json` lists MangaDex titles with read chapters and the last page reached.
 */
object PrototypeLibrary {
    /** Where the prototype kept its library on this PC, if it's there. */
    fun defaultFile(env: (String) -> String? = System::getenv): File? =
        env("APPDATA")
            ?.let { File(it, "Neko/Neko Desktop/data/library.json") }
            ?.takeIf { it.isFile }

    /** True when [json] looks like the prototype's library rather than a Beako backup. */
    fun looksLikeOne(json: JsonElement): Boolean =
        json is JsonObject &&
            "format" !in json &&
            json["entries"].items().firstOrNull()?.get("manga") != null

    fun toLibrary(json: JsonElement, now: Long = System.currentTimeMillis()): LibraryData {
        val progress = (json["progress"] as? JsonObject).orEmpty()
        val entries =
            json["entries"].items().mapNotNull { entry ->
                val manga = entry["manga"] ?: return@mapNotNull null
                val id = manga.str("id") ?: return@mapNotNull null
                val p = progress[id]
                val read = p["read"].items().mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
                // The prototype counted seconds; Beako counts milliseconds.
                val updated = (p["updated"] as? JsonPrimitive)?.longOrNull?.times(1000)
                LibraryEntry(
                    title = title(id, manga),
                    mangaDexId = id,
                    addedAt =
                        (entry["added"] as? JsonPrimitive)?.longOrNull?.times(1000)?.takeIf {
                            it > 0
                        } ?: now,
                    readChapterIds = read,
                    progress =
                        p.str("chapter_id")?.let { chapter ->
                            Progress(
                                chapterId = chapter,
                                chapterNumber = null,
                                page = p.int("page") ?: 0,
                                updatedAt = updated ?: now,
                            )
                        },
                )
            }
        return LibraryData(entries = entries)
    }

    private fun title(id: String, manga: JsonElement): Title {
        val origin = manga.str("original_language")
        return Title(
            source = Sources.MANGADEX,
            id = id,
            title = manga.str("title").orEmpty(),
            altTitles = listOfNotNull(manga.str("alt_title")),
            description = manga.str("description").orEmpty(),
            coverUrl = manga.str("cover_file")?.let { "${MangaDexApi.COVERS}/$id/$it.512.jpg" },
            year = manga.int("year"),
            status =
                when (manga.str("status")) {
                    "ongoing" -> PublicationStatus.ONGOING
                    "completed" -> PublicationStatus.COMPLETED
                    "cancelled" -> PublicationStatus.CANCELLED
                    "hiatus" -> PublicationStatus.HIATUS
                    else -> null
                },
            type = ComicType.fromOrigin(origin),
            contentRating = manga.str("content_rating"),
            demographic = manga.str("demographic"),
            tags = manga["tags"].items().mapNotNull { (it as? JsonPrimitive)?.content },
        )
    }

    internal fun parse(text: String): JsonElement = BeakoJson.parseToJsonElement(text)
}
