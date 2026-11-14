package org.nekomanga.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.state.TitleUpdate
import org.nekomanga.desktop.state.UpdateChecker
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    /** A MangaDex feed whose chapter list can grow between checks. */
    private var chapterIds = listOf("c1")

    private val feed = HttpFetcher { _ ->
        val data =
            chapterIds.joinToString(",") { id ->
                """{"id":"$id","type":"chapter","attributes":{"title":null,"volume":null,"chapter":"${id.drop(1)}","translatedLanguage":"en","publishAt":"2026-11-01T00:00:00+00:00","readableAt":"2026-11-01T00:00:00+00:00","pages":10},"relationships":[]}"""
            }
        HttpResponse(
            200,
            "application/json",
            """{"result":"ok","limit":500,"offset":0,"total":${chapterIds.size},"data":[$data]}""",
        )
    }

    @Test
    fun aSecondCheckReportsNewChaptersAndNotifies() = runTest {
        val store = LibraryStore(File(Files.createTempDirectory("beako").toFile(), "library.json"))
        val title = Title(Sources.COMICK, "hid", "Sample")
        store.add(title, mangaDexId = "md1")
        val mangaDex = MangaDexApi(feed)
        val noNetwork = HttpFetcher { error("not used") }
        val found = mutableListOf<List<TitleUpdate>>()
        val checker =
            UpdateChecker(
                backgroundScope,
                store,
                mangaDex,
                Discovery(ComickApi(noNetwork), mangaDex),
            ) {
                found += it
            }

        checker.runNow()
        runCurrent()
        assertTrue(found.isEmpty(), "the first check only records what exists")

        chapterIds = listOf("c1", "c2", "c3")
        checker.runNow()
        runCurrent()

        assertEquals(listOf("c2", "c3"), found.single().single().chapters.map { it.id })
        assertEquals(2, store.entry(title)!!.newChapters.size)
        assertEquals(0, checker.failures)
    }

    @Test
    fun summaryNamesTheFirstTitleAndCountsTheRest() {
        fun ch(n: String) = Chapter(Sources.MANGADEX, "c$n", n, language = "en", publishedAt = "")
        val one = TitleUpdate(Title(Sources.COMICK, "a", "Eleceed"), listOf(ch("391")))
        val two = TitleUpdate(Title(Sources.COMICK, "b", "Other"), listOf(ch("5"), ch("6")))
        assertEquals("1 new chapter: Eleceed 391", UpdateChecker.summary(listOf(one)))
        assertEquals(
            "3 new chapters: Eleceed 391 and 1 more title",
            UpdateChecker.summary(listOf(one, two)),
        )
    }

    @Test
    fun twoQuickRequestsRunOneCheck() = runTest {
        val store = LibraryStore(File(Files.createTempDirectory("beako").toFile(), "library.json"))
        store.add(Title(Sources.COMICK, "hid", "Sample"), mangaDexId = "md1")
        var requests = 0
        val counting = HttpFetcher { url ->
            requests++
            feed.get(url)
        }
        val mangaDex = MangaDexApi(counting)
        val checker =
            UpdateChecker(
                backgroundScope,
                store,
                mangaDex,
                Discovery(ComickApi(counting), mangaDex),
            )

        val first = checker.runNow()
        val second = checker.runNow()
        runCurrent()

        assertTrue(first === second)
        assertEquals(1, requests)
    }

    @Test
    fun skipRulesLeaveOutTheRightTitles() {
        val base =
            org.nekomanga.shared.library.LibraryEntry(
                org.nekomanga.shared.model.Title(
                    org.nekomanga.shared.model.Sources.MANGADEX,
                    "m",
                    "Done",
                    status = org.nekomanga.shared.model.PublicationStatus.COMPLETED,
                ),
                addedAt = 0,
            )
        val finished = base.copy(chapterCount = 2, readChapterIds = setOf("a", "b"))
        val behind = base.copy(chapterCount = 3, readChapterIds = setOf("a"))
        val fresh = base.copy(chapterCount = 3)
        assertTrue(UpdateChecker.skips(setOf("COMPLETED"), finished))
        assertTrue(!UpdateChecker.skips(setOf("COMPLETED"), behind))
        assertTrue(UpdateChecker.skips(setOf("UNREAD"), behind))
        assertTrue(UpdateChecker.skips(setOf("NOT_STARTED"), fresh))
        assertTrue(!UpdateChecker.skips(emptySet(), finished))
    }
}
