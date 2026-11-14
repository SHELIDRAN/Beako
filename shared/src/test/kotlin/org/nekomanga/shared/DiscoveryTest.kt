package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.discovery.FallbackReason
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.ExternalIds
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

class DiscoveryTest {

    private val mangaDex = MangaDexApi(FakeHttp { json(fixture("mangadex/search.json")) })

    private fun discovery(comick: FakeHttp) = Discovery(ComickApi(comick), mangaDex)

    @Test
    fun comickAnswersWhenItCan() = runTest {
        val outcome =
            discovery(FakeHttp { json(fixture("comick/search.json")) })
                .search(ComickFilters(query = "eleceed"))

        assertEquals(Sources.COMICK, outcome.servedBy)
        assertNull(outcome.fallback)
        assertEquals("YFGuryrn", outcome.titles.single().id)
    }

    @Test
    fun keywordSearchFallsBackToMangaDexWhenComickIsBlocked() = runTest {
        val outcome = discovery(cloudflare).search(ComickFilters(query = "eleceed"))

        assertEquals(Sources.MANGADEX, outcome.servedBy)
        assertEquals(FallbackReason.COMICK_UNAVAILABLE, outcome.fallback)
        assertEquals("Eleceed", outcome.titles.first().title)
    }

    @Test
    fun keywordSearchFallsBackWhenComickFindsNothing() = runTest {
        val outcome = discovery(FakeHttp { json("[]") }).search(ComickFilters(query = "eleceed"))

        assertEquals(FallbackReason.COMICK_FOUND_NOTHING, outcome.fallback)
    }

    @Test
    fun tagBrowsingNeverFallsBack() = runTest {
        val tagged = ComickFilters(includedTags = listOf("female-protagonist"))
        assertFailsWith<ApiError.BlockedByChallenge> { discovery(cloudflare).search(tagged) }

        // No match for a tag filter is an empty Comick result, not a MangaDex search.
        val empty = discovery(FakeHttp { json("[]") }).search(tagged)
        assertEquals(Sources.COMICK, empty.servedBy)
        assertNull(empty.fallback)
    }

    @Test
    fun aRejectedRequestIsNotTreatedAsAnOutage() = runTest {
        val bad = FakeHttp { json("""{"message":"bad"}""", 400) }
        assertFailsWith<ApiError.Rejected> {
            discovery(bad).search(ComickFilters(query = "eleceed"))
        }
    }

    @Test
    fun comickTitleIsFoundOnMangaDexBySharedTrackerId() = runTest {
        val fromComick =
            Title(
                source = Sources.COMICK,
                id = "hid",
                title = "A Different Name",
                externalIds = ExternalIds(anilist = "106929"),
            )
        val match = discovery(offline).findOnMangaDex(fromComick)
        assertEquals("7e544761-7d3d-4fce-8137-719814d7d138", match?.id)
    }

    @Test
    fun comickTitleIsFoundOnMangaDexByExactName() = runTest {
        val fromComick = Title(source = Sources.COMICK, id = "hid", title = "ELECEED!")
        assertEquals("Eleceed", discovery(offline).findOnMangaDex(fromComick)?.title)

        val unknown = Title(source = Sources.COMICK, id = "hid", title = "Something Else")
        assertNull(discovery(offline).findOnMangaDex(unknown))
    }

    @Test
    fun similarTitlesShareTheRarestGenresAndLeaveOutTheTitleItself() = runTest {
        val genres =
            """[{"id":1,"name":"Action","slug":"action","comic_count":40000,"group":"Genre"},
               {"id":2,"name":"Regression","slug":"regression","comic_count":900,"group":"Theme"},
               {"id":3,"name":"Long Strip","slug":"long-strip","comic_count":20000,"group":"Format"},
               {"id":4,"name":"Murim","slug":"murim","comic_count":300,"group":"Theme"}]"""
        val http = FakeHttp { url ->
            if (url.contains("/genre/")) json(genres)
            else json("""[{"hid":"self","title":"Sample"},{"hid":"other","title":"Another"}]""")
        }
        val title = Title(Sources.COMICK, "self", "Sample", comickGenreIds = listOf(1, 2, 3, 4))

        val similar = discovery(http).similar(title)

        assertEquals(listOf("other"), similar.map { it.id })
        // Rarest story genres first; formats like Long Strip are never used.
        val search = http.urls.first { it.contains("/search/") }
        assertTrue(search.contains("genres=murim") && search.contains("genres=regression"))
        assertTrue(!search.contains("long-strip"))
    }

    @Test
    fun similarMatchesMangaDexTagsByName() = runTest {
        val http = FakeHttp { url ->
            if (url.contains("/genre/"))
                json(
                    """[{"id":9,"name":"Isekai","slug":"isekai","comic_count":5000,"group":"Theme"}]""",
                )
            else json("[]")
        }
        discovery(http).similar(Title(Sources.MANGADEX, "md", "X", tags = listOf("Isekai")))
        assertTrue(http.urls.any { it.contains("genres=isekai") })
        // Nothing usable: no search at all.
        val none = FakeHttp { json("[]") }
        assertTrue(discovery(none).similar(Title(Sources.MANGADEX, "md", "X")).isEmpty())
    }
}
