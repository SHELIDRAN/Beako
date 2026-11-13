package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources

/** Runs against responses captured from the live MangaDex API. */
class MangaDexApiTest {

    private fun api(http: FakeHttp) = MangaDexApi(http)

    @Test
    fun searchMapsTitlesCoversAndTrackerIds() = runTest {
        val http = FakeHttp { json(fixture("mangadex/search.json")) }
        val page = api(http).search("eleceed", limit = 3)

        val eleceed = page.items.first()
        assertEquals(Sources.MANGADEX, eleceed.source)
        assertEquals("7e544761-7d3d-4fce-8137-719814d7d138", eleceed.id)
        assertEquals("Eleceed", eleceed.title)
        assertTrue("일렉시드" in eleceed.altTitles)
        assertEquals(ComicType.MANHWA, eleceed.type)
        assertEquals("106929", eleceed.externalIds.anilist)
        assertEquals("147171", eleceed.externalIds.myAnimeList)
        assertEquals("4mnd0g3", eleceed.externalIds.mangaUpdates)
        assertTrue("Action" in eleceed.tags)
        val cover = assertNotNull(eleceed.coverUrl)
        assertTrue(cover.startsWith("${MangaDexApi.COVERS}/${eleceed.id}/"))
        assertTrue(cover.endsWith(".512.jpg"))

        val url = http.urls.single()
        assertTrue(url.startsWith("https://api.mangadex.org/manga?title=eleceed&limit=3&offset=0"))
        assertTrue("includes%5B%5D=cover_art" in url)
        assertTrue("contentRating%5B%5D=safe" in url)
    }

    @Test
    fun chaptersCarryNumberGroupAndPages() = runTest {
        val http = FakeHttp { json(fixture("mangadex/feed.json")) }
        val page = api(http).chapters("7e544761-7d3d-4fce-8137-719814d7d138", limit = 5)

        assertEquals(5, page.items.size)
        val newest = page.items.first()
        assertEquals("390", newest.number)
        assertEquals("en", newest.language)
        assertEquals(18, newest.pages)
        assertNotNull(newest.group)
        assertTrue(page.hasMore)
    }

    @Test
    fun pagesBuildFullImageUrls() = runTest {
        val http = FakeHttp { json(fixture("mangadex/athome.json")) }
        val full = api(http).pages("chapter-id")
        val saver = api(http).pages("chapter-id", dataSaver = true)

        assertEquals(18, full.urls.size)
        assertTrue(full.urls.all { "/data/" in it && it.startsWith("https://") })
        assertTrue(saver.urls.all { "/data-saver/" in it })
    }

    @Test
    fun statisticsReturnFollowsAndRating() = runTest {
        val id = "7e544761-7d3d-4fce-8137-719814d7d138"
        val http = FakeHttp { json(fixture("mangadex/statistics.json")) }
        val stats = assertNotNull(api(http).statistics(listOf(id))[id])
        assertTrue(assertNotNull(stats.follows) > 0)
        assertTrue(assertNotNull(stats.rating) in 0.0..10.0)
    }
}
