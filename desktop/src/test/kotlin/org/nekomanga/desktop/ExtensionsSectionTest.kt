package org.nekomanga.desktop

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.desktop.screens.ExtensionsSection
import org.nekomanga.desktop.state.ExtensionsModel
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.shared.extensions.ExtensionManager
import org.nekomanga.shared.extensions.ExtensionResponse
import org.nekomanga.shared.extensions.ExtensionTransport
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalTestApi::class)
class ExtensionsSectionTest {

    @Test
    fun repositoriesListTheirSourcesAndInstallingAsksFirst() = runComposeUiTest {
        val index =
            """[{"name":"Sample JS","id":1,"lang":"en","version":"1.0.0","sourceCodeUrl":"https://repo.test/a.js","sourceCodeLanguage":1},
               {"name":"Sample Dart","id":2,"lang":"en","version":"1.0.0","sourceCodeUrl":"https://repo.test/b.dart","sourceCodeLanguage":0},
               {"name":"Adult","id":3,"lang":"en","isNsfw":true,"sourceCodeLanguage":1}]"""
        val http = HttpFetcher { HttpResponse(200, "application/json", index) }
        val manager =
            ExtensionManager(
                File(Files.createTempDirectory("beako").toFile(), "ext"),
                http,
                ExtensionTransport { ExtensionResponse(200, "", emptyMap(), it.url) },
            )
        runBlocking { manager.addRepository("https://repo.test/index.json") }
        setContent {
            val scope = rememberCoroutineScope()
            BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                ExtensionsSection(
                    androidx.compose.runtime.remember { ExtensionsModel(scope, manager) },
                )
            }
        }
        waitForIdle()

        onNodeWithText("Sample JS").assertIsDisplayed()
        onNodeWithText("EN · v1.0.0 · Dart (not supported yet)").assertIsDisplayed()
        // Adult sources stay hidden until the setting is on.
        assertTrue(onAllNodesWithText("Adult").fetchSemanticsNodes().isEmpty())

        onNodeWithText("Install").performClick()
        waitForIdle()
        onNodeWithText("Install Sample JS?").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(manager.installed.isEmpty())
    }
}
