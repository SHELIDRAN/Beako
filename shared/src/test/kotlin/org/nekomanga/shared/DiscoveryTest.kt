package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
                .search(ComickFilters(query = "sample"))

        assertEquals(Sources.COMICK, outcome.servedBy)
        assertNull(outcome.fallback)
        assertEquals(3, outcome.titles.size)
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
}
