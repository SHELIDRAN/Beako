package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.discovery.cleanSynopsis
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpResponse

class SynopsisTest {
    @Test
    fun synopsesAreCleanedOfLinksAndMarkup() {
        val raw =
            "A **girl** meets a [teacher](https://x.test) again.<br>Ten years later…\r\n\r\n---\r\n**Links:**\r\n- [Raw](https://raw.test)"
        assertEquals("A girl meets a teacher again.\nTen years later…", cleanSynopsis(raw))
        assertEquals("Plain text.", cleanSynopsis("Plain text."))
        assertEquals("Story.", cleanSynopsis("Story.\n\nLinks: https://a.test"))
    }

    @Test
    fun comickTitlesGetTheirCatalogEntrysSynopsis() {
        runBlocking {
            val http = FakeHttp { url ->
                if ("/comic/the-slug/" in url)
                    json("""{"comic":{"hid":"h1","title":"T","desc":"From Comick."}}""")
                else json("[]")
            }
            val title = Title(Sources.COMICK, "h1", "T", slug = "the-slug")
            assertEquals(
                "From Comick.",
                Discovery(ComickApi(http), MangaDexApi(offline)).synopsis(title),
            )
            assertTrue(http.urls.single().contains("/v1.0/comic/the-slug/"))
        }
    }

    @Test
    fun otherTitlesFallBackToMangaDexThenAComickTitleOfTheSameName() {
        runBlocking {
            // MangaDex lookup fails here: the Comick search by name answers.
            val comick = FakeHttp { url ->
                if ("/search" in url)
                    json("""[{"hid":"x","title":"Source Only","desc":"Found by name."}]""")
                else HttpResponse(404, null, "")
            }
            val fromSource = Title("ext:1", "u", "Source Only")
            assertEquals(
                "Found by name.",
                Discovery(ComickApi(comick), MangaDexApi(offline))
                    .synopsis(fromSource, mangaDexId = "md"),
            )
            assertNull(
                Discovery(ComickApi(FakeHttp { json("[]") }), MangaDexApi(offline))
                    .synopsis(fromSource),
            )
        }
    }
}
