package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.net.OkHttpFetcher
import org.nekomanga.shared.net.beakoUserAgent

/**
 * Talks to the real MangaDex API through the real HTTP client. Skipped unless `BEAKO_LIVE=1`, so
 * ordinary builds and CI never depend on the network.
 */
class LiveMangaDexTest {

    @Test
    fun searchChaptersAndPagesWorkEndToEnd() {
        assumeTrue(System.getenv("BEAKO_LIVE") == "1")
        val api = MangaDexApi(OkHttpFetcher(OkHttpClient(), beakoUserAgent("test")))
        runBlocking {
            val title = api.search("eleceed", limit = 5).items.first { it.title == "Eleceed" }
            val chapters = api.chapters(title.id, limit = 20).items
            val readable = chapters.first { it.pages > 0 && it.externalUrl == null }
            val pages = api.pages(readable.id)
            assertTrue(pages.urls.size == readable.pages)
            println("LIVE OK: ${title.title}, ch ${readable.number}, ${pages.urls.size} pages")
        }
    }
}
