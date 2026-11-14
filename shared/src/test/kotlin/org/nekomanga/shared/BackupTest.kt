package org.nekomanga.shared

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.nekomanga.shared.backup.Backups
import org.nekomanga.shared.backup.BeakoBackup
import org.nekomanga.shared.library.LibraryData
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.Settings
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

@OptIn(ExperimentalSerializationApi::class)
class BackupTest {

    private val dir = Files.createTempDirectory("beako").toFile()
    private val title = Title(Sources.MANGADEX, "m1", "Sample")

    @Test
    fun beakoBackupsRoundTripAndMergeWithoutLosingLocalProgress() {
        val source = LibraryStore(File(dir, "a.json"))
        source.add(title, listOf("Favourites"))
        source.setRead(title, listOf("c1", "c2"), true)
        val file = File(dir, "b.${BeakoBackup.EXTENSION}")
        Backups.write(
            file,
            BeakoBackup(
                createdAt = 1,
                library = source.data.value,
                settings = Settings(dataSaver = true),
            ),
        )

        val (library, settings) = Backups.read(file)
        assertEquals(true, settings?.dataSaver)

        val target = LibraryStore(File(dir, "c.json"))
        target.add(title)
        target.setRead(title, listOf("c3"), true)
        target.add(Title(Sources.COMICK, "x", "Other"))
        assertEquals(0, target.merge(library))
        val merged = target.entry(title)!!
        assertEquals(setOf("c1", "c2", "c3"), merged.readChapterIds)
        assertEquals(listOf("Favourites"), merged.categories)
        assertEquals(2, target.data.value.entries.size)
    }

    // Neko's backup shape, with extra fields Beako doesn't read.
    @Serializable
    class NekoChapter(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(4) val read: Boolean,
        @ProtoNumber(9) val chapterNumber: Float = 1f,
    )

    @Serializable
    class NekoManga(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(3) val title: String,
        @ProtoNumber(5) val author: String = "Someone",
        @ProtoNumber(16) val chapters: List<NekoChapter> = emptyList(),
        @ProtoNumber(17) val categories: List<Int> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(901) val scanlatorFilter: String? = "x",
    )

    @Serializable
    class NekoCategory(@ProtoNumber(1) val name: String, @ProtoNumber(2) val order: Int)

    @Serializable
    class NekoFile(
        @ProtoNumber(1) val backupManga: List<NekoManga>,
        @ProtoNumber(2) val backupCategories: List<NekoCategory>,
    )

    @Test
    fun nekoBackupsBringMangaDexTitlesReadChaptersAndCategories() {
        val id = "a96676e5-8ae2-425e-b549-7f15dd34a6d8"
        val neko =
            NekoFile(
                listOf(
                    NekoManga(
                        2499283573021220255,
                        "/title/$id",
                        "Komi",
                        chapters =
                            listOf(
                                NekoChapter(
                                    "/chapter/11111111-2222-3333-4444-555555555555",
                                    "Ch. 1",
                                    true,
                                ),
                                NekoChapter(
                                    "/chapter/66666666-2222-3333-4444-555555555555",
                                    "Ch. 2",
                                    false,
                                ),
                            ),
                        categories = listOf(1),
                    ),
                    NekoManga(1, "/manga/not-a-uuid", "Elsewhere"),
                    NekoManga(1, "/title/$id-x", "Not favourite", favorite = false),
                ),
                listOf(NekoCategory("Reading", 1), NekoCategory("Later", 2)),
            )
        val bytes =
            ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use {
                    it.write(ProtoBuf.encodeToByteArray(NekoFile.serializer(), neko))
                }
            }
        val file =
            File(dir, "neko_2026-11-14_10-00.tachibk").apply { writeBytes(bytes.toByteArray()) }

        val (library, settings) = Backups.read(file)

        assertNull(settings)
        val entry = library.entries.single()
        assertEquals(id, entry.title.id)
        assertEquals(id, entry.mangaDexId)
        assertEquals(setOf("11111111-2222-3333-4444-555555555555"), entry.readChapterIds)
        assertEquals(listOf("Reading"), entry.categories)
        assertEquals(listOf("Reading", "Later"), library.categories)
    }

    @Test
    fun unreadableFilesAreRejectedAndAutomaticBackupsKeepAWeek() {
        val junk = File(dir, "junk.tachibk").apply { writeText("not a backup") }
        assertFailsWith<IOException> { Backups.read(junk) }

        val folder = File(dir, "auto")
        val backup = { BeakoBackup(createdAt = 0, library = LibraryData(), settings = Settings()) }
        for (day in 1..9) Backups.automatic(folder, backup, LocalDate.of(2026, 11, day))
        assertNull(Backups.automatic(folder, backup, LocalDate.of(2026, 11, 9)), "once a day")
        val names = folder.list()!!.sorted()
        assertEquals(7, names.size)
        assertTrue(names.first().contains("2026-11-03"))
    }

    @Test
    fun restoredMangaDexTitlesMergeIntoTheSameSeriesFromComick() {
        val store = LibraryStore(File(dir, "m.json"))
        val fromComick = Title(Sources.COMICK, "hid", "Eleceed")
        store.add(fromComick, mangaDexId = "md-uuid")
        val restored =
            LibraryStore(File(dir, "r.json")).apply {
                add(Title(Sources.MANGADEX, "md-uuid", "Eleceed"), mangaDexId = "md-uuid")
                setRead(Title(Sources.MANGADEX, "md-uuid", "Eleceed"), listOf("c9"), true)
            }
        assertEquals(0, store.merge(restored.data.value))
        assertEquals(1, store.data.value.entries.size)
        assertEquals(setOf("c9"), store.entry(fromComick)!!.readChapterIds)
    }
}
