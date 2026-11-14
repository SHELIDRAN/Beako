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
    fun searchParsesARealResponse() = runTest {
        val http = FakeHttp { json(fixture("comick/search.json")) }
        val eleceed = ComickApi(http).search(ComickFilters(query = "eleceed")).single()

        assertEquals("YFGuryrn", eleceed.id)
        assertEquals("04-eleceed", eleceed.slug)
        assertEquals("Eleceed", eleceed.title)
        // The medium cover built from md_covers wins over the small cover_url.
        assertEquals("https://meo.comick.pictures/8w3j7-m.jpg", eleceed.coverUrl)
        assertEquals(ComicType.MANHWA, eleceed.type)
        assertEquals(PublicationStatus.ONGOING, eleceed.status)
        assertEquals(9.37, eleceed.rating)
        assertEquals(117332, eleceed.follows)
        assertEquals(426, eleceed.chapterCount)
        assertEquals("shounen", eleceed.demographic)
        assertEquals("2026-11-10T19:00:03.145Z", eleceed.lastUploadedAt)
        assertEquals("106929", eleceed.externalIds.anilist)
        assertEquals("147171", eleceed.externalIds.myAnimeList)
        assertEquals("57", eleceed.externalIds.mangaBaka)
    }

    @Test
    fun titlesParseLenientlyWhenFieldsAreMissingOrOddlyTyped() = runTest {
        val body =
            """
            [
              {"hid": "aB3xYz", "title": "Akuyaku Reijou no Sample", "is_english_title": false,
               "md_titles": [{"title": "The Sample Villainess", "lang": "en"}],
               "rating": 8.7, "cover_url": "https://meo.comick.pictures/small-s.jpg",
               "external_ids": {"myanimelist": 147171, "mangabaka": null}},
              {"hid": "fR1nCh", "title": "Sample BD", "country": "fr",
               "md_covers": [{"b2key": "nodot"}]}
            ]
            """
        val titles = ComickApi(FakeHttp { json(body) }).search(ComickFilters())

        // The English alternative title wins over a romanized main title.
        assertEquals("The Sample Villainess", titles[0].title)
        assertTrue("Akuyaku Reijou no Sample" in titles[0].altTitles)
        assertEquals(8.7, titles[0].rating)
        assertEquals("https://meo.comick.pictures/small-s.jpg", titles[0].coverUrl)
        // A numeric ID is read as text; null stays null.
        assertEquals("147171", titles[0].externalIds.myAnimeList)
        assertNull(titles[0].externalIds.mangaBaka)
        assertEquals(ComicType.OTHER, titles[1].type)
        assertEquals("https://meo.comick.pictures/nodot", titles[1].coverUrl)
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
    fun clickingATagCyclesIncludeExcludeClear() {
        val none = ComickFilters()
        val included = none.cycleTag("magic")
        assertEquals(ComickFilters.Use.INCLUDED, included.tagUse("magic"))
        val excluded = included.cycleTag("magic")
        assertEquals(ComickFilters.Use.EXCLUDED, excluded.tagUse("magic"))
        assertEquals(listOf("magic"), excluded.excludedTags)
        val cleared = excluded.cycleTag("magic")
        assertEquals(ComickFilters.Use.NONE, cleared.tagUse("magic"))
        assertEquals(none, cleared)
    }

    @Test
    fun anEleventhIncludedTagIsNotAllowed() {
        val full = (1..10).fold(ComickFilters()) { f, i -> f.cycleTag("t$i") }
        assertEquals(10, full.includedTags.size)
        // The include step is skipped because the API takes at most 10.
        val next = full.cycleTag("t11")
        assertEquals(10, next.includedTags.size)
        assertEquals(ComickFilters.Use.EXCLUDED, next.tagUse("t11"))
    }

    @Test
    fun activeCountAndClearedIgnoreKeywordAndSort() {
        val filters =
            ComickFilters(query = "x", sort = ComickSort.POPULAR)
                .cycleTag("magic")
                .cycleGenre("romance")
                .copy(status = 1, translationCompleted = true)
        assertEquals(4, filters.activeCount)
        val cleared = filters.cleared()
        assertEquals(0, cleared.activeCount)
        assertEquals("x", cleared.query)
        assertEquals(ComickSort.POPULAR, cleared.sort)
    }

    @Test
    fun othersTypeIsFilteredLocallyBecauseComickHasNoCountryCodeForIt() = runTest {
        val filters = ComickFilters(types = setOf(ComicType.OTHER))
        assertTrue(filters.toQuery().none { it.first == "country" })

        val body =
            """[{"hid": "kr1", "title": "A", "country": "kr"}, {"hid": "fr1", "title": "B", "country": "fr"}]"""
        val titles = ComickApi(FakeHttp { json(body) }).search(filters)
        assertEquals(listOf("fr1"), titles.map { it.id })
    }

    @Test
    fun mostRecentPopularReadsTheTrendingListForItsWindow() = runTest {
        val http = FakeHttp { json(fixture("comick/top_trending.json")) }
        val api = ComickApi(http)

        assertEquals(
            listOf("uDSpI8XM", "kS_eJ8jw"),
            api.chart(ComickChart.MOST_RECENT_POPULAR, days = 7).map { it.id },
        )
        assertEquals(
            listOf("QwogT7H5", "hiFEZykH"),
            api.chart(ComickChart.MOST_RECENT_POPULAR, days = 30).map { it.id },
        )
        assertTrue(http.urls.first().startsWith("https://api.comick.dev/top?type=trending&day=7"))
    }

    @Test
    fun risingReadsRecentRankFromTheHomeFeeds() = runTest {
        val http = FakeHttp { json(fixture("comick/top.json")) }
        val titles = ComickApi(http).chart(ComickChart.RISING)

        assertEquals(listOf("QwogT7H5", "hiFEZykH", "AmyTA493"), titles.map { it.id })
        assertTrue(titles.all { it.coverUrl != null })
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
    fun genresCarryTheirGroup() = runTest {
        val genres = ComickApi(FakeHttp { json(fixture("comick/genre.json")) }).genres()
        assertEquals("4-koma", genres.first().slug)
        assertEquals("format", genres.first().group)
    }

    @Test
    fun tagsAreSortedByUseAndOnesWithoutTitlesDropped() = runTest {
        val http = FakeHttp { json(fixture("comick/category.json")) }
        val tags = ComickApi(http).tags()

        assertEquals(listOf("full-color", "webtoon", "big-breasts"), tags.map { it.slug })
        assertEquals("Full Color", tags.first().name)
        assertEquals(9589, tags.first().count)
    }

    @Test
    fun coversFromPngOriginalsUseComicksJpegThumbnail() {
        runTest {
            // Real chart entries whose md_covers keys are .png: Comick's thumbnails are always
            // .jpg.
            val http = FakeHttp { json(fixture("comick/top_trending.json")) }
            val covers =
                ComickApi(http).chart(ComickChart.MOST_RECENT_POPULAR, days = 7).mapNotNull {
                    it.coverUrl
                }
            assertTrue(covers.isNotEmpty())
            assertTrue(covers.all { it.endsWith("-m.jpg") }, covers.toString())
            assertTrue("https://meo.comick.pictures/Ygy80b-m.jpg" in covers)
        }
    }
}
