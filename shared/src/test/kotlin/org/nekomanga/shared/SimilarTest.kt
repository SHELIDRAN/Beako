package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import org.nekomanga.shared.discovery.Similar
import org.nekomanga.shared.discovery.SimilarWeights
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

class SimilarTest {
    private val title =
        Title(
            Sources.COMICK,
            "me",
            "Me",
            type = ComicType.MANHWA,
            comickGenreIds = listOf(1, 2, 3, 4),
        )

    private fun candidate(
        id: String,
        genres: List<Int>,
        follows: Int,
        rating: Double,
        type: ComicType,
        year: Int,
    ) =
        Title(
            Sources.COMICK,
            id,
            id,
            comickGenreIds = genres,
            follows = follows,
            rating = rating,
            type = type,
            year = year,
        )

    // Each candidate wins on exactly one quality.
    private val pool =
        listOf(
            candidate("popular", listOf(1), 90_000, 6.0, ComicType.MANGA, 2010),
            candidate("close", listOf(1, 2, 3, 4), 100, 6.0, ComicType.MANGA, 2010),
            candidate("rated", listOf(1), 100, 9.5, ComicType.MANGA, 2010),
            candidate("sametype", listOf(1), 100, 6.0, ComicType.MANHWA, 2010),
            candidate("new", listOf(1), 100, 6.0, ComicType.MANGA, 2026),
        )

    private fun first(weights: SimilarWeights) = Similar.rank(title, pool, weights).first().id

    private val off = SimilarWeights(0f, 0f, 0f, 0f, 0f)

    @Test
    fun eachWeightPutsItsBestCandidateFirst() {
        assertEquals("close", first(off.copy(genres = 1f)))
        assertEquals("popular", first(off.copy(popularity = 1f)))
        assertEquals("rated", first(off.copy(rating = 1f)))
        assertEquals("sametype", first(off.copy(sameType = 1f)))
        assertEquals("new", first(off.copy(newer = 1f)))
    }

    @Test
    fun allOffKeepsThePoolsOrderAndTheLimitApplies() {
        assertEquals(pool.map { it.id }, Similar.rank(title, pool, off).map { it.id })
        assertEquals(2, Similar.rank(title, pool, SimilarWeights(), limit = 2).size)
        assertEquals(emptyList(), Similar.rank(title, emptyList(), SimilarWeights()))
    }

    @Test
    fun mangaDexTitlesMatchByTagName() {
        val md = Title(Sources.MANGADEX, "m", "M", tags = listOf("Romance", "Comedy"))
        val pool =
            listOf(
                Title(Sources.COMICK, "a", "A", tags = listOf("Action")),
                Title(Sources.COMICK, "b", "B", tags = listOf("romance", "comedy")),
            )
        assertEquals("b", Similar.rank(md, pool, off.copy(genres = 1f)).first().id)
    }
}
