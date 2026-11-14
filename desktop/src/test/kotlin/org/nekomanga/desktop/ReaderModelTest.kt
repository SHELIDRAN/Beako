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
}
