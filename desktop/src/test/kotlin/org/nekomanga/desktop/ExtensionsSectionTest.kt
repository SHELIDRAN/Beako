package org.nekomanga.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun sourcesFilterAndSortByLanguage() = runComposeUiTest {
        val index =
            """[{"name":"Alpha","id":1,"lang":"ja","version":"1","sourceCodeUrl":"https://repo.test/a.js","sourceCodeLanguage":1},
               {"name":"Beta","id":2,"lang":"en","version":"1","sourceCodeUrl":"https://repo.test/b.js","sourceCodeLanguage":1},
               {"name":"Gamma","id":3,"lang":"en","version":"1","sourceCodeUrl":"https://repo.test/c.js","sourceCodeLanguage":1}]"""
        val http = HttpFetcher { HttpResponse(200, "application/json", index) }
        val manager =
            ExtensionManager(
                File(Files.createTempDirectory("beako").toFile(), "ext"),
                http,
                ExtensionTransport { ExtensionResponse(200, "", emptyMap(), it.url) },
            )
        runBlocking { manager.addRepository("https://repo.test/index.json") }
        var language by androidx.compose.runtime.mutableStateOf("")
        var byLanguage by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            val scope = rememberCoroutineScope()
            BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                ExtensionsSection(
                    androidx.compose.runtime.remember { ExtensionsModel(scope, manager) },
                    language = language,
                    byLanguage = byLanguage,
                    onLanguage = { language = it },
                    onByLanguage = { byLanguage = it },
                )
            }
        }
        waitForIdle()
        // The language menu lists each language with its count, most sources first.
        onNodeWithText("Language: All").performClick()
        waitForIdle()
        onNodeWithText("English · 2").assertIsDisplayed()
        onNodeWithText("Japanese · 1").performClick()
        waitForIdle()
        assertEquals("ja", language)
        onNodeWithText("Alpha").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Beta").fetchSemanticsNodes().isEmpty())
        // Sorted by language, all languages: a heading per language, English before Japanese.
        language = ""
        onNodeWithText("Sort: Name").performClick()
        waitForIdle()
        onNodeWithText("Language").performClick()
        waitForIdle()
        assertTrue(byLanguage)
        onNodeWithText("English").assertIsDisplayed()
        onNodeWithText("Japanese").assertIsDisplayed()
        assertEquals("Portuguese (Brazil)", org.nekomanga.desktop.screens.languageName("pt-br"))
        assertEquals("Multiple languages", org.nekomanga.desktop.screens.languageName("all"))
    }
}
