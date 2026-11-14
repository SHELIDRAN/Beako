package org.nekomanga.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.Palette
import org.nekomanga.desktop.theme.SidebarMode

@OptIn(ExperimentalTestApi::class)
class BeakoAppTest {

    private val still =
        BeakoAppearance(reduceMotion = true, sidebarMode = SidebarMode.ALWAYS_EXPANDED)

    @Test
    fun sidebarListsEveryScreenAndStartsOnBrowse() = runComposeUiTest {
        setContent { BeakoApp(still) }
        Destination.entries.forEach { onNodeWithContentDescription(it.label).assertIsDisplayed() }
        onNodeWithContentDescription("Browse").assertIsSelected()
        onNodeWithContentDescription("Library").assertIsNotSelected()
        onNodeWithContentDescription("Connect Comick").assertIsDisplayed()
        onNodeWithContentDescription("Connect MangaDex").assertIsDisplayed()
    }

    @Test
    fun clickingASidebarItemSwitchesScreen() = runComposeUiTest {
        setContent {
            BeakoApp(
                still,
                screen = { destination ->
                    androidx.compose.material3.Text("screen:${destination.name}")
                },
            )
        }
        onNodeWithText("screen:BROWSE").assertIsDisplayed()

        onNodeWithContentDescription("Library").performClick()

        onNodeWithContentDescription("Library").assertIsSelected()
        onNodeWithText("screen:LIBRARY").assertIsDisplayed()
    }

    @Test
    fun appRendersWithGlassTurnedOffAndCollapsedSidebar() = runComposeUiTest {
        setContent {
            BeakoApp(
                BeakoAppearance(reduceTransparency = true, sidebarMode = SidebarMode.ICONS_ONLY),
            )
        }
        onNodeWithContentDescription("Reading history").assertIsDisplayed()
    }

    @Test
    fun dropletSwitchReportsTheChosenOption() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent {
            BeakoTheme(still) {
                DropletSegmented(listOf("7d", "1m", "3m"), selected, { selected = it }, "Window")
            }
        }
        onNodeWithText("7d").assertIsSelected()

        onNodeWithText("3m").performClick()

        assertEquals(2, selected)
        onNodeWithText("3m").assertIsSelected()
    }

    @Test
    fun paletteAndScaleApplyAcrossTheApp() = runComposeUiTest {
        var appearance by mutableStateOf(still.copy(palette = Palette.MIDNIGHT, scale = 1.25f))
        setContent { BeakoApp(appearance) }
        waitForIdle()
        assertEquals(Palette.MIDNIGHT.ground, BeakoColors.Ground)
        onNodeWithContentDescription("Browse").assertIsDisplayed()

        appearance = appearance.copy(palette = Palette.OLED)
        waitForIdle()
        assertEquals(Palette.OLED.ground, BeakoColors.Ground)
        BeakoColors.palette = Palette.NEUTRAL
    }
}
