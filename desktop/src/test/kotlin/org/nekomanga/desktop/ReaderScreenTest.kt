package org.nekomanga.desktop

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.nekomanga.desktop.screens.ReaderScreen
import org.nekomanga.desktop.state.ReaderModel
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalTestApi::class)
class ReaderScreenTest {

    private val threePages = HttpFetcher {
        HttpResponse(
            200,
            "application/json",
            """{"result":"ok","baseUrl":"https://img.test","chapter":{"hash":"h","data":["1.jpg","2.jpg","3.jpg"],"dataSaver":[]}}""",
        )
    }

    private val chapters =
        listOf("1", "2").map {
            Chapter(
                Sources.MANGADEX,
                "c$it",
                it,
                language = "en",
                publishedAt = "2026-01-01",
                pages = 3,
            )
        }

    private val manhwa = Title(Sources.MANGADEX, "m", "Sample Tower", type = ComicType.MANHWA)

    @Test
    fun webtoonShowsPagesAndControlsThenSwitchesToPagedMode() = runComposeUiTest {
        lateinit var model: ReaderModel
        var closed = false
        setContent {
            val scope = rememberCoroutineScope()
            model =
                androidx.compose.runtime.remember {
                    ReaderModel(scope, MangaDexApi(threePages), manhwa, chapters, 0)
                }
            BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                ReaderScreen(model, onClose = { closed = true })
            }
        }
        waitForIdle()

        assertEquals(ReadingMode.WEBTOON, model.mode)
        onNodeWithText("Sample Tower").assertIsDisplayed()
        onNodeWithText("Chapter 1").assertIsDisplayed()
        onNodeWithText("1 of 3").assertIsDisplayed()
        onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()

        onNodeWithText("Left to right").performClick()
        waitForIdle()
        assertEquals(ReadingMode.LEFT_TO_RIGHT, model.mode)

        // The right-hand zone turns forward in left-to-right mode.
        onNodeWithContentDescription("Right side: turn the page").performClick()
        waitForIdle()
        assertEquals(1, model.page)
        onNodeWithText("2 of 3").assertIsDisplayed()

        // The settings panel shows the paged-mode options.
        onNodeWithContentDescription("Reader settings (O)").performClick()
        waitForIdle()
        onNodeWithText("Page fit").assertIsDisplayed()
        onNodeWithText("Split wide pages").assertIsDisplayed()
        onNodeWithContentDescription("Close reader settings (Esc)").performClick()
        waitForIdle()

        onNodeWithContentDescription("Next chapter (])").performClick()
        waitForIdle()
        assertEquals(1, model.index)
        onNodeWithText("Chapter 2").assertIsDisplayed()
        // The clicked button had focus; arrow keys must still turn pages in the new chapter (0.1.2
        // QA).
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(1, model.page, "the arrow key turns the page after a chapter change")
        onNodeWithContentDescription("Close reader (Esc)").assertIsDisplayed()

        onNodeWithContentDescription("Close reader (Esc)").performClick()
        assertEquals(true, closed)
    }
}
