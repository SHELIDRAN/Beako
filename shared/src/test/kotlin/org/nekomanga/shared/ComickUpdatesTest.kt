package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.model.ComicType

class ComickUpdatesTest {
    @Test
    fun latestChapterReleasesListTheirTitlesNotTheirChapters() {
        runBlocking {
            val http = FakeHttp { json(fixture("comick/chapter-updates.json")) }
            val updates = ComickApi(http).updates(hot = true, page = 2, language = "fr").updates
            assertEquals(
                listOf("Pick Me Up", "The Archmage Curriculum", "I Alone Level Up: Ragnarok"),
                updates.map { it.title.title },
            )
            assertEquals("99sr80kM", updates[0].title.id)
            assertEquals("229", updates[0].chapter)
            assertEquals(listOf("ARVEN"), updates[0].groups)
            assertEquals("kr", updates[0].title.country)
            assertTrue(updates[0].title.coverUrl!!.endsWith("-m.jpg"))
            val url = http.urls.single()
            assertTrue(
                "/chapter/?" in url && "order=hot" in url && "page=2" in url && "lang=fr" in url,
            )
            // Only the list endpoint: never a chapter's own page or its images.
            assertTrue("get_images" !in url)
        }
    }

    @Test
    fun typesAreMatchedFromEachTitlesCountry() {
        runBlocking {
            val http = FakeHttp { json(fixture("comick/chapter-updates.json")) }
            val manga = ComickApi(http).updates(hot = false, types = setOf(ComicType.MANGA))
            assertEquals(0, manga.updates.size)
            // No matches on this page, but Comick had entries: there are more pages to read.
            assertTrue(manga.more)
            assertEquals(
                3,
                ComickApi(http).updates(hot = false, types = setOf(ComicType.MANHWA)).updates.size,
            )
        }
    }
}
