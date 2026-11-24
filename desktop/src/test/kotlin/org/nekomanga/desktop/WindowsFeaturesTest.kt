package org.nekomanga.desktop

import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.nekomanga.desktop.screens.CropBorders
import org.nekomanga.desktop.screens.Tap
import org.nekomanga.desktop.screens.tapAt
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.UpdateChecker
import org.nekomanga.desktop.state.groupsOf
import org.nekomanga.desktop.state.withoutArticle
import org.nekomanga.desktop.state.withoutDuplicates
import org.nekomanga.desktop.theme.CoverColour
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.local.LocalSource
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class WindowsFeaturesTest {

    // ───────────── Local manga ─────────────

    @Test
    fun localMangaReadsFoldersAndArchivesInOrder() {
        val root = Files.createTempDirectory("beako-local").toFile()
        val cache = Files.createTempDirectory("beako-cache").toFile()
        val series = root.resolve("Sample Tower").apply { mkdirs() }
        series.resolve("cover.jpg").writeBytes(byteArrayOf(1))
        // Folders of images, numbered so "10" sorts after "2".
        for (n in listOf("Chapter 2", "Chapter 10", "Chapter 1")) {
            series.resolve(n).mkdirs()
            series.resolve(n).resolve("02.jpg").writeBytes(byteArrayOf(2))
            series.resolve(n).resolve("01.jpg").writeBytes(byteArrayOf(1))
        }
        // An archive chapter, with a folder inside and a non-image to skip.
        ZipOutputStream(series.resolve("Vol. 2 Ch. 11.cbz").outputStream()).use { zip ->
            for ((name, bytes) in
                listOf(
                    "pages/b.png" to byteArrayOf(2),
                    "pages/a.png" to byteArrayOf(1),
                    "info.txt" to byteArrayOf(0),
                )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        root.resolve("Empty series").mkdirs()
        val local = LocalSource({ root }, cache)

        val titles = local.titles()
        assertEquals(listOf("Empty series", "Sample Tower"), titles.map { it.title })
        val sample = titles.last()
        assertEquals(Sources.LOCAL, sample.source)
        assertTrue(sample.coverUrl!!.endsWith("cover.jpg"))

        val chapters = local.chapters(sample)
        assertEquals(listOf("11", "10", "2", "1"), chapters.map { it.number })
        assertEquals("2", chapters.first().volume)

        val folderPages = local.pages(chapters.last())
        assertEquals(listOf("01.jpg", "02.jpg"), folderPages.map { it.name })
        val archivePages = local.pages(chapters.first())
        assertEquals(2, archivePages.size)
        assertEquals(listOf<Byte>(1, 2), archivePages.map { it.readBytes().single() })
        // Unpacked once: a second read uses the cache.
        assertEquals(archivePages, local.pages(chapters.first()))
    }

    @Test
    fun localChapterIdsCannotReachOutsideTheFolder() {
        val root = Files.createTempDirectory("beako-local").toFile()
        val outside =
            root.parentFile.resolve("beako-secret-${System.nanoTime()}").apply { mkdirs() }
        outside.resolve("1.jpg").writeBytes(byteArrayOf(1))
        val local = LocalSource({ root }, Files.createTempDirectory("c").toFile())
        val sneaky =
            Chapter(
                source = Sources.LOCAL,
                id = "../${outside.name}",
                number = "1",
                language = "",
                publishedAt = "",
                pages = 0,
            )
        assertTrue(local.pages(sneaky).isEmpty())
    }

    @Test
    fun chapterNumbersAreReadFromFileNames() {
        assertEquals("12" to null, LocalSource.numbers("Chapter 12"))
        assertEquals("13" to "2", LocalSource.numbers("Vol. 2 Ch. 13"))
        assertEquals("12.5" to null, LocalSource.numbers("c012.50"))
        assertEquals("7" to null, LocalSource.numbers("007"))
    }

    // ───────────── Library and chapters ─────────────

    private fun ch(number: String?, group: String?, id: String = "$number-$group") =
        Chapter(
            source = Sources.MANGADEX,
            id = id,
            number = number,
            language = "en",
            group = group,
            publishedAt = "",
            pages = 1,
        )

    @Test
    fun duplicatesKeepTheGroupReadBefore() {
        val order =
            listOf(
                ch("1", "Alpha"),
                ch("2", "Beta"),
                ch("2", "Alpha"),
                ch("3", "Beta"),
                ch(null, "Extra"),
                ch("4", "Gamma"),
            )
        assertEquals(
            listOf("1-Alpha", "2-Alpha", "3-Beta", "null-Extra", "4-Gamma"),
            withoutDuplicates(order).map { it.id },
        )
        assertEquals(listOf("A", "B"), groupsOf(ch("1", "A & B")))
    }

    @Test
    fun articlesDontDecideAlphabeticalOrder() {
        assertEquals("apothecary diaries", withoutArticle("the apothecary diaries"))
        assertEquals("silent voice", withoutArticle("a silent voice"))
        assertEquals("theatre", withoutArticle("theatre"))
        assertEquals("a", withoutArticle("a"))
    }

    @Test
    fun categoriesCanBeRenamedMovedAndUpdatesFiltered() {
        val store =
            LibraryStore(Files.createTempFile("library", ".json").toFile().apply { delete() })
        val title = Title(Sources.MANGADEX, "m1", "Sample")
        store.add(title, listOf("Reading", "Later"))
        store.addCategory("Done")
        store.renameCategory("Later", "Someday")
        store.moveCategory("Done", -1)
        assertEquals(listOf("Reading", "Done", "Someday"), store.data.value.categories)
        assertEquals(listOf("Reading", "Someday"), store.entry(title)!!.categories)

        val entry = store.entry(title)!!
        assertTrue(UpdateChecker.inCategories(emptySet(), emptySet(), entry))
        assertTrue(UpdateChecker.inCategories(setOf("Reading"), emptySet(), entry))
        assertFalse(UpdateChecker.inCategories(setOf("Done"), emptySet(), entry))
        assertFalse(UpdateChecker.inCategories(emptySet(), setOf("Someday"), entry))

        store.setTrack(title, Track("comick", "r1", status = TrackStatus.DROPPED))
        assertTrue(UpdateChecker.skips(setOf("DROPPED"), store.entry(title)!!))
        assertFalse(UpdateChecker.skips(emptySet(), store.entry(title)!!))
    }

    // ───────────── Reader ─────────────

    @Test
    fun clickZoneLayoutsMapToTheRightSides() {
        assertEquals(Tap.LEFT, tapAt("DEFAULT", 0.1f, 0.5f))
        assertEquals(Tap.MENU, tapAt("DEFAULT", 0.5f, 0.5f))
        assertEquals(Tap.RIGHT, tapAt("KINDLE", 0.5f, 0.5f))
        assertEquals(Tap.MENU, tapAt("KINDLE", 0.5f, 0.05f))
        assertEquals(Tap.MENU, tapAt("EDGE", 0.3f, 0.5f))
        assertEquals(Tap.RIGHT, tapAt("EDGE", 0.95f, 0.5f))
        assertEquals(Tap.LEFT, tapAt("L_SHAPE", 0.5f, 0.1f))
        assertEquals(Tap.RIGHT, tapAt("L_SHAPE", 0.5f, 0.9f))
        assertEquals(Tap.MENU, tapAt("DISABLED", 0.1f, 0.5f))
    }

    private val fourPages = HttpFetcher {
        HttpResponse(
            200,
            "application/json",
            """{"result":"ok","baseUrl":"https://img.test","chapter":{"hash":"h","data":["1.jpg","2.jpg","3.jpg","4.jpg"],"dataSaver":[]}}""",
        )
    }

    private val manga = Title(Sources.MANGADEX, "m", "Sample", type = ComicType.MANGA)

    private fun chapter(n: String) =
        Chapter(
            source = Sources.MANGADEX,
            id = "c$n",
            number = n,
            language = "en",
            publishedAt = "",
            pages = 4,
        )

    @Test
    fun twoPagesTurnTogetherAndTheTransitionShowsBeforeTheNextChapter() {
        runTest {
            val model =
                ReaderModel(
                    backgroundScope,
                    MangaDexApi(fourPages),
                    manga,
                    listOf(chapter("1"), chapter("2")),
                    0,
                    transitions = { true },
                )
            model.start()
            runCurrent()
            model.twoPages = true
            model.turn(true)
            assertEquals(2, model.page)
            model.turn(true)
            // Past the last pair: the transition, still in chapter 1.
            assertTrue(model.atTransition)
            assertEquals(0, model.index)
            model.turn(false)
            assertFalse(model.atTransition)
            model.turn(true)
            model.turn(true)
            runCurrent()
            assertEquals(1, model.index)
            assertFalse(model.atTransition)
            // Back by a pair, too.
            model.turn(true)
            assertEquals(2, model.page)
            model.turn(false)
            assertEquals(0, model.page)
        }
    }

    @Test
    fun nextChapterSkipsChaptersAlreadyRead() {
        runTest {
            val read = setOf("c2", "c3")
            val model =
                ReaderModel(
                    backgroundScope,
                    MangaDexApi(fourPages),
                    manga,
                    listOf(chapter("1"), chapter("2"), chapter("3"), chapter("4")),
                    0,
                    isRead = { it.id in read },
                    skipRead = { true },
                )
            assertEquals(3, model.nextIndex)
            model.nextChapter()
            runCurrent()
            assertEquals(3, model.index)
        }
    }

    // ───────────── Images ─────────────

    private fun bitmap(w: Int, h: Int, colour: (Int, Int) -> Int): Bitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL))
        val canvas = org.jetbrains.skia.Canvas(bitmap)
        val paint = org.jetbrains.skia.Paint()
        for (y in 0 until h) for (x in 0 until w) {
            paint.color = colour(x, y)
            canvas.drawRect(
                org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f),
                paint,
            )
        }
        return bitmap
    }

    @Test
    fun cropBordersTrimsPlainMargins() {
        runTest {
            // A 100×100 white page with a dark block from 20 to 80.
            val page =
                bitmap(100, 100) { x, y ->
                    if (x in 20 until 80 && y in 20 until 80) 0xFF202020.toInt() else -1
                }
            val cropped = CropBorders.transform(page, coil3.size.Size.ORIGINAL)
            assertTrue(cropped.width in 60..70, "width ${cropped.width}")
            assertTrue(cropped.height in 60..70, "height ${cropped.height}")
            // A busy page (no plain corner) is left alone.
            val busy =
                bitmap(64, 64) { x, y ->
                    if ((x + y) % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
                }
            assertEquals(64, CropBorders.transform(busy, coil3.size.Size.ORIGINAL).width)
        }
    }

    @Test
    fun coverColourFindsTheVividHueAndIgnoresGrey() {
        val red =
            CoverColour.dominant(40, 40) { x, _ ->
                if (x < 30) 0xFFD02020.toInt() else 0xFF808080.toInt()
            }
        assertNotNull(red)
        assertTrue(red.red > 0.6f && red.green < 0.3f, "picked $red")
        assertNull(CoverColour.dominant(40, 40) { _, _ -> 0xFF777777.toInt() })
        assertNull(CoverColour.dominant(40, 40) { _, _ -> 0xFF000000.toInt() })
        // Unused import guard.
        assertEquals(Color.Red, Color.Red)
        assertEquals("key", LibraryEntry.keyOf(Title("s", "key", "t")).substringAfter(':'))
    }
}
