package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.mangadex.MangaDexCredentials
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.HttpTransport

class MangaDexFeedTest {

    @Test
    fun followFeedAsksForNewestChaptersAndKeepsEachChaptersTitle() {
        runBlocking {
            val requests = mutableListOf<HttpRequest>()
            val transport = HttpTransport { request ->
                requests += request
                if (request.url.contains("openid-connect/token"))
                    HttpResponse(
                        200,
                        "application/json",
                        """{"access_token":"a","expires_in":900,"refresh_token":"r"}""",
                    )
                else HttpResponse(200, "application/json", fixture("mangadex/feed.json"))
            }
            val account = MangaDexAccount(transport, MemoryVault())
            account.signIn(MangaDexCredentials("reader", "pw", "client", "secret"))

            val page = account.feed(offset = 0, limit = 5, languages = listOf("en", "fr"))

            val url = requests.last().url
            assertTrue(url.startsWith("https://api.mangadex.org/user/follows/manga/feed?"))
            assertTrue("order%5BreadableAt%5D=desc" in url)
            assertTrue("translatedLanguage%5B%5D=en" in url && "translatedLanguage%5B%5D=fr" in url)
            assertEquals("Bearer a", requests.last().headers["Authorization"])
            assertEquals(5, page.items.size)
            assertEquals("7e544761-7d3d-4fce-8137-719814d7d138", page.items[0].mangaId)
            assertTrue(page.hasMore)
        }
    }

    @Test
    fun titlesAreLookedUpByIdInTheOrderAsked() {
        runBlocking {
            val http = FakeHttp { json(fixture("mangadex/search.json")) }
            val api = MangaDexApi(http)
            val found = api.titles(listOf("unknown", "7e544761-7d3d-4fce-8137-719814d7d138"))
            assertEquals(listOf("7e544761-7d3d-4fce-8137-719814d7d138"), found.map { it.id })
            val url = http.urls.single()
            assertTrue("ids%5B%5D=unknown" in url && "includes%5B%5D=cover_art" in url)
            assertEquals(emptyList(), api.titles(emptyList()))
            assertEquals(1, http.urls.size)
        }
    }

    @Test
    fun popularNewTitlesAreLimitedToRecentlyAdded() {
        runBlocking {
            val http = FakeHttp { json(fixture("mangadex/search.json")) }
            MangaDexApi(http).search(null, createdAtSince = "2026-10-26T00:00:00")
            assertTrue("createdAtSince=2026-10-26T00%3A00%3A00" in http.urls.single())
        }
    }
}
