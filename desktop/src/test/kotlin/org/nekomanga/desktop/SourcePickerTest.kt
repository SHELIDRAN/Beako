package org.nekomanga.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import org.nekomanga.desktop.screens.DetailsScreen
import org.nekomanga.desktop.state.DetailsModel
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.download.DownloadManager
import org.nekomanga.shared.download.ImageFetcher
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalTestApi::class)
class SourcePickerTest {

    @Test
    fun notAvailableStillOpensThePickerWithASearch() {
        runComposeUiTest {
            val dir = Files.createTempDirectory("beako").toFile()
            val http = HttpFetcher {
                HttpResponse(
                    200,
                    "application/json",
                    """{"result":"ok","data":[],"total":0,"limit":10,"offset":0}""",
                )
            }
            setContent {
                val scope = rememberCoroutineScope()
                val model = remember {
                    val mangaDex = MangaDexApi(http)
                    DetailsModel(
                        scope,
                        Discovery(ComickApi(http), mangaDex),
                        mangaDex,
                        LibraryStore(File(dir, "library.json")),
                        DownloadManager(
                            dir,
                            scope,
                            { error("unused") },
                            ImageFetcher { error("unused") },
                        ),
                        Title(
                            Sources.COMICK,
                            "hid",
                            "The Weakest Awakened Rises Through Slime Synthesis",
                            altTitles = listOf("最弱覚醒者"),
                        ),
                    )
                }
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    DetailsScreen(model, onBack = {}, onRead = { _, _, _ -> })
                }
            }
            waitForIdle()

            onNodeWithText("Not available on any source").performClick()
            waitForIdle()
            onNodeWithText("Where to read").assertIsDisplayed()
            onNodeWithText("MangaDex").assertIsDisplayed()
            onNodeWithText("No automatic match").assertIsDisplayed()
            onNodeWithText("Install sources in More → Sources to read from more sites.")
                .assertIsDisplayed()

            onNodeWithText("Search").performClick()
            waitForIdle()
            onNodeWithText("Search MangaDex").assertIsDisplayed()
            // Alternative names are one click away.
            onNode(hasText("最弱覚醒者") and hasClickAction()).assertIsDisplayed()
        }
    }
}
