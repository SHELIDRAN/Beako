package org.nekomanga.shared.backup

import java.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.nekomanga.shared.library.LibraryData
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

/**
 * Reads Android Neko backups. Only the fields Beako uses are declared; protobuf skips the rest.
 * Field numbers follow Neko's `eu.kanade.tachiyomi.data.backup.models` (Apache-2.0).
 */
@OptIn(ExperimentalSerializationApi::class)
object NekoBackup {

    @Serializable
    class Backup(
        @ProtoNumber(1) val backupManga: List<Manga> = emptyList(),
        @ProtoNumber(2) val backupCategories: List<Category> = emptyList(),
    )

    @Serializable
    class Manga(
        @ProtoNumber(1) val source: Long = 0,
        @ProtoNumber(2) val url: String = "",
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(16) val chapters: List<Chapter> = emptyList(),
        @ProtoNumber(17) val categories: List<Int> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
    )

    @Serializable
    class Chapter(
        @ProtoNumber(1) val url: String = "",
        @ProtoNumber(4) val read: Boolean = false,
    )

    @Serializable
    class Category(@ProtoNumber(1) val name: String = "", @ProtoNumber(2) val order: Int = 0)

    fun decode(bytes: ByteArray): Backup =
        try {
            ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        } catch (e: SerializationException) {
            throw IOException("This file isn’t a backup Beako can read.", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("This file isn’t a backup Beako can read.", e)
        }

    /**
     * Library titles from a Neko backup: favourites whose URL holds a MangaDex id. Titles from
     * other Neko sources are skipped, since Beako reads chapters from MangaDex and extensions only.
     */
    fun toLibrary(backup: Backup, now: Long = System.currentTimeMillis()): LibraryData {
        // Neko stores a manga's categories by their order number.
        val categoryNames = backup.backupCategories.associate { it.order to it.name }
        val entries =
            backup.backupManga
                .filter { it.favorite }
                .mapNotNull { manga ->
                    val id = mangaDexId(manga.url) ?: return@mapNotNull null
                    val title =
                        Title(
                            source = Sources.MANGADEX,
                            id = id,
                            title = manga.title.ifBlank { id },
                            description = manga.description.orEmpty(),
                            coverUrl = manga.thumbnailUrl,
                            tags = manga.genre,
                        )
                    LibraryEntry(
                        title = title,
                        mangaDexId = id,
                        categories = manga.categories.mapNotNull { categoryNames[it] }.distinct(),
                        addedAt = manga.dateAdded.takeIf { it > 0 } ?: now,
                        readChapterIds =
                            manga.chapters
                                .filter { it.read }
                                .mapNotNull { mangaDexId(it.url) }
                                .toSet(),
                    )
                }
                .distinctBy { it.key }
        return LibraryData(
            entries = entries,
            categories =
                backup.backupCategories
                    .sortedBy { it.order }
                    .map { it.name }
                    .filter { it.isNotBlank() },
        )
    }

    private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** The MangaDex UUID in a Neko URL such as `/title/<id>` or `/chapter/<id>`. */
    internal fun mangaDexId(url: String): String? =
        UUID.findAll(url.lowercase()).lastOrNull()?.value
}
