package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.state.displayName
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderModelTest {

    /** Every chapter has three pages. */
    private val threePages = HttpFetcher {
        HttpResponse(
            200,
            "application/json",
            """{"result":"ok","baseUrl":"https://img.test","chapter":{"hash":"h","data":["1.jpg","2.jpg","3.jpg"],"dataSaver":[]}}""",
        )
    }

    private fun chapter(number: String, external: String? = null) =
        Chapter(
            source = Sources.MANGADEX,
            id = "c$number",
            number = number,
            language = "en",
            publishedAt = "2026-01-01T00:00:00+00:00",
            pages = 3,
            externalUrl = external,
        )

    private val manga = Title(Sources.MANGADEX, "m", "Sample", type = ComicType.MANGA)

    @Test
    fun turningPastTheLastPageOpensTheNextChapter() = runTest {
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1"), chapter("2")),
                0,
            )
        model.start()
        runCurrent()
        assertEquals(3, model.pageCount)

        model.turn(true)
        model.turn(true)
        assertEquals(2, model.page)
        model.turn(true)
        runCurrent()

        assertEquals(1, model.index)
        assertEquals(0, model.page)
    }

    @Test
    fun turningBackFromTheFirstPageOpensThePreviousChapterAtItsEnd() = runTest {
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1"), chapter("2")),
                1,
            )
        model.start()
        runCurrent()

        model.turn(false)
        runCurrent()

        assertEquals(0, model.index)
        assertEquals(2, model.page)
        // At the very start, turning back does nothing.
        model.turn(false)
        model.turn(false)
        model.turn(false)
        runCurrent()
        assertEquals(0, model.index)
        assertEquals(0, model.page)
    }

    @Test
    fun publisherHostedChapterLoadsNoPages() = runTest {
        val fails = HttpFetcher { error("must not be requested") }
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(fails),
                manga,
                listOf(chapter("1", "https://publisher.test")),
                0,
            )
        model.start()
        runCurrent()

        assertIs<Loadable.Ready<*>>(model.pages)
        assertEquals(0, model.pageCount)
    }

    @Test
    fun readingModeDefaultsFollowTheTitleType() {
        assertEquals(ReadingMode.WEBTOON, ReadingMode.defaultFor(ComicType.MANHWA))
        assertEquals(ReadingMode.RIGHT_TO_LEFT, ReadingMode.defaultFor(ComicType.MANGA))
        assertTrue(ReadingMode.LEFT_TO_RIGHT.isPaged)
    }

    @Test
    fun chapterNamesReadNaturally() {
        assertEquals("Chapter 12.5", chapter("12.5").displayName())
        assertEquals(
            "Vol. 2 Chapter 3 · Dawn",
            chapter("3").copy(volume = "2", title = "Dawn").displayName(),
        )
        assertEquals("Oneshot", chapter("1").copy(number = null).displayName())
    }

    private fun library(): LibraryStore =
        LibraryStore(
                java.io.File(
                    java.nio.file.Files.createTempDirectory("beako").toFile(),
                    "library.json",
                ),
            )
            .apply { add(manga) }

    @Test
    fun pagingBackIntoAChapterDoesNotMarkItRead() = runTest {
        val store = library()
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1"), chapter("2")),
                1,
                store,
            )
        model.start()
        runCurrent()

        model.turn(false)
        runCurrent()

        assertEquals(0, model.index)
        assertEquals(2, model.page)
        assertTrue(store.entry(manga)!!.readChapterIds.isEmpty())
    }

    @Test
    fun readingToTheEndOrMovingOnMarksTheChapterRead() = runTest {
        val store = library()
        val finished = mutableListOf<String>()
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1"), chapter("2"), chapter("3")),
                0,
                store,
                onChapterRead = { finished += it.id },
            )
        model.start()
        runCurrent()
        model.page = 2
        assertEquals(setOf("c1"), store.entry(manga)!!.readChapterIds)

        // Skipping ahead from page 1 also counts the chapter as finished.
        model.nextChapter()
        runCurrent()
        model.nextChapter()
        runCurrent()
        assertEquals(setOf("c1", "c2"), store.entry(manga)!!.readChapterIds)
        // Trackers hear about each finished chapter.
        assertEquals(listOf("c1", "c2"), finished.distinct())
    }

    @Test
    fun resumingOpensAtTheSavedPage() = runTest {
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1")),
                0,
                library(),
                startPage = 2,
            )
        model.start()
        runCurrent()
        assertEquals(2, model.page)
    }

    @Test
    fun longStripPagesSwitchToWebtoonUnlessAModeWasChosen() = runTest {
        val auto =
            ReaderModel(backgroundScope, MangaDexApi(threePages), manga, listOf(chapter("1")), 0)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, auto.mode)
        auto.reportAspect(0, 3.1f)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, auto.mode, "one tall page isn't enough")
        auto.reportAspect(1, 2.9f)
        assertEquals(ReadingMode.WEBTOON, auto.mode)
        assertTrue(auto.autoSwitched)

        val saved = mutableListOf<ReadingMode>()
        val chosen =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1")),
                0,
                savedMode = ReadingMode.LEFT_TO_RIGHT,
                saveMode = { saved += it },
            )
        chosen.reportAspect(0, 3f)
        chosen.reportAspect(1, 3f)
        assertEquals(ReadingMode.LEFT_TO_RIGHT, chosen.mode)
        chosen.mode = ReadingMode.WEBTOON
        assertEquals(listOf(ReadingMode.WEBTOON), saved)
    }

    @Test
    fun splitSpreadsTurnHalfByHalf() = runTest {
        val model =
            ReaderModel(
                backgroundScope,
                MangaDexApi(threePages),
                manga,
                listOf(chapter("1")),
                0,
                splitWidePages = { true },
            )
        model.start()
        runCurrent()
        model.reportAspect(1, 0.7f) // page 2 is a spread
        model.turn(true)
        assertEquals(1 to 0, model.page to model.half)
        model.turn(true)
        assertEquals(1 to 1, model.page to model.half)
        model.turn(true)
        assertEquals(2 to 0, model.page to model.half)
        // Paging back enters the spread on its second half.
        model.turn(false)
        assertEquals(1 to 1, model.page to model.half)
        model.turn(false)
        model.turn(false)
        assertEquals(0 to 0, model.page to model.half)
        // Webtoon never splits.
        model.mode = ReadingMode.WEBTOON
        assertTrue(!model.isSplit(1))
    }
}
