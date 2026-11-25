package org.nekomanga.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.components.Sidebar
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.MangaDexModel
import org.nekomanga.desktop.state.MangaDexTab
import org.nekomanga.desktop.state.SearchModel
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.MotionFeel
import org.nekomanga.desktop.theme.SidebarMode
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.HttpTransport

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class BrowseExtrasTest {

    /** A Comick catalog of three pages of 20 titles; later pages are empty. */
    private class Catalog : HttpFetcher {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): HttpResponse {
            urls += url
            if (!url.contains("/search")) return HttpResponse(200, "application/json", "[]")
            val page = Regex("""[?&]page=(\d+)""").find(url)?.groupValues?.get(1)?.toInt() ?: 1
            val items =
                if (page > 3) ""
                else
                    (1..20).joinToString(",") {
                        """{"hid":"p$page-$it","title":"Title $page-$it"}"""
                    }
            return HttpResponse(200, "application/json", "[$items]")
        }
    }

    @Test
    fun randomPicksComeFromPagesMatchingTheFiltersAndAreMixed() {
        runTest {
            val catalog = Catalog()
            val offline = HttpFetcher { throw IOException("offline") }
            val model =
                SearchModel(
                    this,
                    Discovery(ComickApi(catalog), MangaDexApi(offline)),
                    ComickApi(catalog),
                    random = Random(7),
                )
            model.update { it.copy(includedTags = listOf("female-lead")) }
            model.randomPicks(count = 12)
            advanceUntilIdle()
            assertEquals(Loadable.Ready(Unit), model.status)
            assertTrue(model.randomPicks)
            assertEquals(12, model.titles.size)
            assertEquals(12, model.titles.map { it.id }.toSet().size)
            assertTrue(model.titles.all { it.id.substringBefore('-').drop(1).toInt() <= 3 })
            assertTrue(catalog.urls.filter { "/search" in it }.all { "female-lead" in it })
            // Back to results leaves random picks right away.
            model.backToResults()
            assertTrue(!model.randomPicks)
            model.randomPicks(count = 12)
            advanceUntilIdle()
            // Changing a filter goes back to ordinary results.
            model.update { it.copy(includedTags = emptyList()) }
            assertTrue(!model.randomPicks)
        }
    }

    @Test
    fun aFailedShuffleStaysInRandomPicksSoTryAgainShufflesAgain() {
        runTest {
            val down = HttpFetcher { HttpResponse(503, "text/html", "down") }
            val model =
                SearchModel(this, Discovery(ComickApi(down), MangaDexApi(down)), ComickApi(down))
            model.randomPicks()
            advanceUntilIdle()
            assertTrue(model.status is Loadable.Failed)
            assertTrue(model.randomPicks)
        }
    }

    @Test
    fun mangaDexSearchWaitsForAQuery() {
        runTest {
            val requests = mutableListOf<String>()
            val api =
                MangaDexApi(
                    HttpFetcher { url ->
                        requests += url
                        throw IOException("offline")
                    },
                )
            val account =
                MangaDexAccount(HttpTransport { throw IOException("unused") }, MemoryVault())
            val model = MangaDexModel(this, api, account)
            model.select(MangaDexTab.SEARCH)
            model.start()
            advanceUntilIdle()
            requests.clear()
            model.reload()
            advanceUntilIdle()
            assertTrue(requests.isEmpty(), "a blank search asks MangaDex nothing")
            model.search("frieren")
            advanceUntilIdle()
            assertTrue(requests.single().contains("title=frieren"))
            assertTrue(requests.single().contains("order%5Brelevance%5D=desc"))
        }
    }

    @Test
    fun calmerMotionBouncesLessAndSlidesSlower() {
        assertTrue(MotionFeel.CALM.damping >= 1f)
        assertTrue(MotionFeel.BALANCED.damping > MotionFeel.LIVELY.damping)
        assertTrue(MotionFeel.CALM.pace > MotionFeel.BALANCED.pace)
        assertTrue(MotionFeel.BALANCED.pace > MotionFeel.LIVELY.pace)
        assertEquals(MotionFeel.BALANCED, MotionFeel.of("nonsense"))
    }

    @Test
    fun searchMangaDexInTheMenuOpensTheSearchTab() {
        runComposeUiTest {
            var asked: Boolean? = null
            setContent {
                BeakoTheme(
                    BeakoAppearance(reduceMotion = true, sidebarMode = SidebarMode.ALWAYS_EXPANDED),
                ) {
                    Sidebar(Destination.BROWSE, {}, onMangaDex = { asked = it })
                }
            }
            onAllNodesWithText("Search MangaDex")[0].performClick()
            assertEquals(true, asked)
        }
    }
}
