package org.nekomanga.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.comick.ComickChart
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.OkHttpFetcher
import org.nekomanga.shared.net.beakoUserAgent

/**
 * Talks to the real Comick catalog API. Skipped unless `BEAKO_LIVE=1`. With `BEAKO_CAPTURE=<dir>`
 * it also saves each raw response there, for refreshing the fixtures.
 */
class LiveComickTest {

    private class Recording(private val real: HttpFetcher, private val dir: File?) : HttpFetcher {
        override suspend fun get(url: String): HttpResponse {
            val response = real.get(url)
            if (dir != null && response.isSuccessful) {
                val name = url.substringAfter("comick.dev/").replace(Regex("[^A-Za-z0-9]+"), "_")
                File(dir, "${name.take(80)}.json").writeText(response.body)
            }
            return response
        }
    }

    @Test
    fun catalogWorksEndToEnd() {
        assumeTrue(System.getenv("BEAKO_LIVE") == "1")
        val dir = System.getenv("BEAKO_CAPTURE")?.let { File(it).apply { mkdirs() } }
        val api = ComickApi(Recording(OkHttpFetcher(OkHttpClient(), beakoUserAgent("test")), dir))
        runBlocking {
            val search = api.search(ComickFilters(query = "eleceed", limit = 3))
            assertTrue(search.isNotEmpty())
            println("LIVE search: ${search.first()}")

            val tags = api.tags()
            val genres = api.genres()
            println("LIVE tags=${tags.size} top=${tags.take(3)} genres=${genres.size}")
            assertTrue(tags.size > 100 && genres.isNotEmpty())

            for (chart in ComickChart.entries) {
                val titles = api.chart(chart, days = 7)
                println(
                    "LIVE $chart: ${titles.size} first=${titles.firstOrNull()?.title} cover=${titles.firstOrNull()?.coverUrl}",
                )
                assertTrue(titles.isNotEmpty())
            }

            // Are included tags all-of or any-of? A rare tag alone, then with a common one:
            // all-of can only shrink the list, any-of fills the page.
            val common = tags[0].slug
            val rare = tags.last { it.count in 5..30 }
            val alone = api.search(ComickFilters(includedTags = listOf(rare.slug), limit = 50))
            val both =
                api.search(ComickFilters(includedTags = listOf(rare.slug, common), limit = 50))
            println(
                "LIVE tag semantics: rare=${rare.slug}(${rare.count}) alone=${alone.size} " +
                    "with $common=${both.size} subset=${alone.map { it.id }.containsAll(both.map { it.id })}",
            )
        }
    }
}
