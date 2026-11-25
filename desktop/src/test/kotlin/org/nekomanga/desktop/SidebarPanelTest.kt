package org.nekomanga.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.nekomanga.desktop.components.AccountSlot
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.components.Sidebar
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.SidebarMode

@OptIn(ExperimentalTestApi::class)
class SidebarPanelTest {

    @Test
    fun expandedPanelShowsSectionsGroupCountsAndAccounts() {
        runComposeUiTest {
            var searched = 0
            var mode: SidebarMode? = null
            setContent {
                var selected by remember { mutableStateOf(Destination.BROWSE) }
                BeakoTheme(
                    BeakoAppearance(reduceMotion = true, sidebarMode = SidebarMode.ALWAYS_EXPANDED),
                ) {
                    Sidebar(
                        selected = selected,
                        onSelect = { selected = it },
                        accounts = listOf(AccountSlot("Comick", "Hana"), AccountSlot("MangaDex")),
                        onSearch = { searched++ },
                        counts = mapOf(Destination.UPDATES to 12),
                        fresh = setOf(Destination.UPDATES),
                        onModeChange = { mode = it },
                    )
                }
            }
            onNodeWithText("Beako").assertIsDisplayed()
            onNodeWithText("MAIN").assertIsDisplayed()
            onNodeWithText("ACCOUNTS").assertIsDisplayed()
            onNodeWithText("12").assertIsDisplayed()
            onNodeWithContentDescription("new", useUnmergedTree = true).assertIsDisplayed()
            // Browse is open on Comick, with each catalog's search one step in.
            onNodeWithContentDescription("Browse").assertIsSelected()
            onAllNodes(androidx.compose.ui.test.hasText("Comick"))[0].assertIsDisplayed()
            onNodeWithText("Search MangaDex").assertIsDisplayed()
            onNodeWithText("Search with tags").performClick()
            assertEquals(1, searched)
            onNodeWithContentDescription("Search Comick (Ctrl+K)").performClick()
            assertEquals(2, searched)
            // Clicking Browse while on it folds the group.
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()
            assertTrue(onAllNodesWithTextCount("Search with tags") == 0)
            onNodeWithContentDescription("Comick: signed in as Hana").assertIsDisplayed()
            onNodeWithContentDescription("Connect MangaDex").assertIsDisplayed()
            // The foot is More; the user's name stands above "Settings".
            onNodeWithContentDescription("More").performClick()
            onNodeWithContentDescription("More").assertIsSelected()
            onNodeWithContentDescription("Collapse menu").performClick()
            assertEquals(SidebarMode.AUTO_COLLAPSE, mode)
        }
    }

    @Test
    fun railKeepsEveryDestinationReachable() {
        runComposeUiTest {
            var mode: SidebarMode? = null
            setContent {
                BeakoTheme(
                    BeakoAppearance(reduceMotion = true, sidebarMode = SidebarMode.ICONS_ONLY),
                ) {
                    Sidebar(Destination.LIBRARY, {}, onModeChange = { mode = it })
                }
            }
            Destination.entries.forEach {
                onNodeWithContentDescription(it.label).assertIsDisplayed()
            }
            onNodeWithContentDescription("Library").assertIsSelected()
            assertEquals(0, onAllNodesWithTextCount("Beako"))
            onNodeWithContentDescription("Keep menu open").performClick()
            assertEquals(SidebarMode.ALWAYS_EXPANDED, mode)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class SidebarAutoCollapseTest {
    @Test
    fun autoCollapseClosesAfterPickingADestination() {
        runComposeUiTest {
            var selected by mutableStateOf(Destination.BROWSE)
            setContent {
                BeakoTheme(
                    BeakoAppearance(reduceMotion = true, sidebarMode = SidebarMode.AUTO_COLLAPSE),
                ) {
                    Sidebar(selected, { selected = it })
                }
            }
            mainClock.autoAdvance = false
            // Hovering opens the panel after its short wait.
            onNodeWithContentDescription("Main menu").performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(400)
            assertEquals(1, onAllNodesWithTextCount("Beako"))
            // Picking a destination closes it, though the pointer and the click's focus remain.
            onNodeWithContentDescription("Library").performClick()
            mainClock.advanceTimeBy(800)
            assertEquals(Destination.LIBRARY, selected)
            assertEquals(0, onAllNodesWithTextCount("Beako"))
            // Leaving and coming back opens it again.
            onNodeWithContentDescription("Main menu").performMouseInput { exit() }
            mainClock.advanceTimeBy(800)
            onNodeWithContentDescription("Main menu").performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(400)
            assertEquals(1, onAllNodesWithTextCount("Beako"))
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
