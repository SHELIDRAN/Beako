package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.comick.ComickChart
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.comick.ComickSort
import org.nekomanga.shared.comick.Demographic
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.PublicationStatus

class ComickApiTest {

    @Test
    fun searchParsesTitlesLeniently() = runTest {
        val http = FakeHttp { json(fixture("comick/search.json")) }
        val titles = ComickApi(http).search(ComickFilters(query = "sample"))

        val first = titles[0]
        assertEquals("aB3xYz", first.id)
        // The English alternative title wins over a romanized main title.
        assertEquals("The Sample Villainess", first.title)
        assertTrue("Akuyaku Reijou no Sample" in first.altTitles)
        assertEquals("https://meo.comick.pictures/abc123-m.jpg", first.coverUrl)
        assertEquals(ComicType.MANGA, first.type)
        assertEquals(PublicationStatus.ONGOING, first.status)
        assertEquals(8.52, first.rating)
        assertEquals(15234, first.follows)
        assertEquals("shoujo", first.demographic)
        assertEquals("106929", first.externalIds.anilist)
        // A numeric ID is read as text.
        assertEquals("147171", first.externalIds.myAnimeList)
        assertNull(first.externalIds.mangaBaka)

        val second = titles[1]
        assertEquals("Sample Hunter", second.title)
        assertEquals("https://meo.comick.pictures/direct-m.jpg", second.coverUrl)
        assertEquals(7.9, second.rating)

        // Missing fields don't break parsing.
        assertEquals(ComicType.OTHER, titles[2].type)
        assertEquals("https://meo.comick.pictures/nodot", titles[2].coverUrl)
    }

    @Test
    fun filtersBecomeQueryParametersWithinApiLimits() {
        val query =
            ComickFilters(
                    query = " villainess ",
                    includedTags = (1..12).map { "tag$it" },
                    excludedTags = listOf("ntr"),
                    includedGenres = listOf("romance"),
                    excludedGenres = (1..45).map { "g$it" },
                    demographics = setOf(Demographic.JOSEI, Demographic.SHOUJO),
                    types = setOf(ComicType.MANHWA, ComicType.MANGA),
                    status = 1,
                    translationCompleted = true,
                    fromYear = 2020,
                    minRating = 7.5,
                    sort = ComickSort.BAYESIAN_RATING,
                    page = 99,
                    limit = 500,
                )
                .toQuery()

        fun values(name: String) = query.filter { it.first == name }.map { it.second }
        assertEquals(listOf("villainess"), values("q"))
        assertEquals(10, values("tags").size)
        assertEquals(listOf("ntr"), values("excluded-tags"))
        assertEquals(40, values("excludes").size)
        assertEquals(listOf("2", "4"), values("demographic"))
        assertEquals(listOf("jp", "kr"), values("country"))
        assertEquals(listOf("safe"), values("content_rating"))
        assertEquals(listOf("true"), values("completed"))
        assertEquals(listOf("rating"), values("sort"))
        assertEquals(listOf("50"), values("page"))
        assertEquals(listOf("50"), values("limit"))
    }

    @Test
    fun othersTypeIsFilteredLocallyBecauseComickHasNoCountryCodeForIt() = runTest {
        val filters = ComickFilters(types = setOf(ComicType.OTHER))
        assertTrue(filters.toQuery().none { it.first == "country" })

        val http = FakeHttp { json(fixture("comick/search.json")) }
        val titles = ComickApi(http).search(filters)
        assertEquals(listOf("fR1nCh"), titles.map { it.id })
    }

    @Test
    fun chartsReadTheRightFeedAndSkipEntriesWithoutCovers() = runTest {
        val http = FakeHttp { json(fixture("comick/top.json")) }
        val api = ComickApi(http)

        assertEquals(
            listOf("recent1"),
            api.chart(ComickChart.MOST_RECENT_POPULAR, days = 7).map { it.id },
        )
        assertEquals(listOf("trend7"), api.chart(ComickChart.TRENDING, days = 7).map { it.id })
        // An empty window falls back to the overall ranking.
        assertEquals(listOf("rank1"), api.chart(ComickChart.TRENDING, days = 30).map { it.id })
        assertTrue(http.urls.all { it.startsWith("https://api.comick.dev/top?day=") })
    }

    @Test
    fun mostFollowedNewIsASearchSortedByNewFollows() = runTest {
        val http = FakeHttp { json(fixture("comick/search.json")) }
        ComickApi(http).chart(ComickChart.MOST_FOLLOWED_NEW, days = 30)

        val url = http.urls.single()
        assertTrue(url.startsWith("https://api.comick.dev/v1.0/search/?"))
        assertTrue("sort=user_follow_count" in url && "time=30" in url)
    }

    @Test
    fun tagsAreSortedByUseAndUnusedOnesDropped() = runTest {
        val http = FakeHttp { json(fixture("comick/category.json")) }
        val tags = ComickApi(http).tags()

        assertEquals(listOf("female-protagonist", "magic"), tags.map { it.slug })
        assertEquals("Female Protagonist", tags.first().name)
        assertEquals(3401, tags.first().count)
    }
}
