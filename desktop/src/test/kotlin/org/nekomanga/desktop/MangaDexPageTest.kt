package org.nekomanga.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.components.Sidebar
import org.nekomanga.desktop.screens.MangaDexScreen
import org.nekomanga.desktop.screens.ago
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.MangaDexModel
import org.nekomanga.desktop.state.MangaDexTab
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.SidebarMode
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpTransport

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MangaDexPageTest {
    private val offline = HttpFetcher { throw IOException("no network") }
    private val noAccount =
        MangaDexAccount(HttpTransport { throw IOException("unused") }, MemoryVault())

    private fun title(id: String) = Title(Sources.MANGADEX, id, "Title $id")

    private fun chapter(id: String) =
        Chapter(
            Sources.MANGADEX,
            id,
            id,
            language = "en",
            publishedAt = "2026-11-24T10:00:00+00:00",
        )

    @Test
    fun feedGroupsChaptersUnderTheirTitleNewestFirst() {
        val groups =
            MangaDexModel.group(
                listOf(
                    "a" to chapter("3"),
                    "b" to chapter("2"),
                    "a" to chapter("1"),
                    "x" to chapter("0"),
                ),
                mapOf("a" to title("a"), "b" to title("b")),
            )
        assertEquals(listOf("a", "b"), groups.map { it.title.id })
        assertEquals(listOf("3", "1"), groups[0].chapters.map { it.id })
    }

    @Test
    fun aCatalogThatCannotLoadSaysWhyAndTheFeedNeedsASignIn() {
        runTest {
            val model = MangaDexModel(this as TestScope, MangaDexApi(offline), noAccount)
            model.start()
            advanceUntilIdle()
            assertIs<Loadable.Failed>(model.status)
            assertTrue(
                (model.status as Loadable.Failed).message.contains("Couldn’t reach MangaDex"),
            )
            model.select(MangaDexTab.FEED)
            advanceUntilIdle()
            assertEquals(false, model.signedIn)
            assertEquals(Loadable.Loading, model.feedStatus)
        }
    }

    @Test
    fun timesReadAsShortAgoLabels() {
        val now = java.time.Instant.parse("2026-11-24T12:00:00Z")
        assertEquals("2 h ago", ago("2026-11-24T10:00:00+00:00", now))
        assertEquals("3 d ago", ago("2026-11-21T11:00:00+00:00", now))
        assertEquals("", ago("not a date", now))
    }

    @Test
    fun signedOutFeedOffersToConnect() {
        runComposeUiTest {
            var connect = 0
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            val model = MangaDexModel(scope, MangaDexApi(offline), noAccount)
            model.select(MangaDexTab.FEED)
            setContent {
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    MangaDexScreen(model, {}, {}, { _, _ -> }, { connect++ })
                }
            }
            onNodeWithText("Sign in to see your feed").assertIsDisplayed()
            onNodeWithText("Connect MangaDex").performClick()
            assertEquals(1, connect)
        }
    }

    @Test
    fun browseGroupOpensTheMangaDexPage() {
        runComposeUiTest {
            var opened = 0
            setContent {
                BeakoTheme(
                    BeakoAppearance(reduceMotion = true, sidebarMode = SidebarMode.ALWAYS_EXPANDED),
                ) {
                    Sidebar(Destination.BROWSE, {}, onMangaDex = { opened++ })
                }
            }
            // The first "MangaDex" is the Browse page; the Accounts list has one too.
            onAllNodesWithText("MangaDex")[0].performClick()
            assertEquals(1, opened)
        }
    }
}
