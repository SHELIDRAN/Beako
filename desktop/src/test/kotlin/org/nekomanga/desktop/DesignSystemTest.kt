package org.nekomanga.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.nekomanga.desktop.components.Alert
import org.nekomanga.desktop.components.AlertKind
import org.nekomanga.desktop.components.ArrowButton
import org.nekomanga.desktop.components.ArrowStyle
import org.nekomanga.desktop.components.IconOption
import org.nekomanga.desktop.components.IconToggleGroup
import org.nekomanga.desktop.components.MorphSelect
import org.nekomanga.desktop.components.Tabs
import org.nekomanga.desktop.components.ToggleIconButton
import org.nekomanga.desktop.components.TriState
import org.nekomanga.desktop.components.TriStateChip
import org.nekomanga.desktop.screens.readerPalette
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.Palette

@OptIn(ExperimentalTestApi::class)
class DesignSystemTest {

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b).toDouble()
    }

    @Test
    fun textKeepsAaContrastOnEveryPalette() {
        val before = BeakoColors.palette
        try {
            for (palette in Palette.entries) {
                BeakoColors.palette = palette
                for (bg in
                    listOf(BeakoColors.Ground, BeakoColors.Surface, BeakoColors.GlassSolid)) {
                    assertTrue(contrast(BeakoColors.Text, bg) >= 12.0, "text on $palette")
                    assertTrue(contrast(BeakoColors.TextMuted, bg) >= 4.5, "muted on $palette")
                    assertTrue(contrast(BeakoColors.Danger, bg) >= 4.5, "danger on $palette")
                }
                // The accent button's white label on its fill.
                assertTrue(contrast(BeakoColors.AccentInk, BeakoColors.AccentFill) >= 4.5)
                // The primary button inverts: ground ink on a text fill.
                assertTrue(contrast(BeakoColors.Ground, BeakoColors.Text) >= 12.0)
            }
        } finally {
            BeakoColors.palette = before
        }
    }

    @Test
    fun porcelainIsTheOneLightPalette() {
        val before = BeakoColors.palette
        try {
            assertEquals(listOf(Palette.PORCELAIN), Palette.entries.filter { it.light })
            BeakoColors.palette = Palette.PORCELAIN
            assertTrue(BeakoColors.Text.luminance() < 0.05f, "dark text on Porcelain")
            BeakoColors.palette = Palette.NEUTRAL
            assertTrue(BeakoColors.Text.luminance() > 0.85f, "light text on Neutral")
            assertEquals(Palette.PORCELAIN, Palette.of("PORCELAIN"))
        } finally {
            BeakoColors.palette = before
        }
    }

    private fun stateIs(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun triStateChipCyclesAndSaysItsState() {
        runComposeUiTest {
            setContent {
                var state by remember { mutableStateOf(TriState.NONE) }
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    TriStateChip(
                        "Female lead",
                        state,
                        { state = TriState.entries[(state.ordinal + 1) % TriState.entries.size] },
                    )
                }
            }
            val chip = onNodeWithContentDescription("Female lead")
            chip.assert(stateIs("Not selected"))
            chip.performClick()
            chip.assert(stateIs("Included"))
            onNodeWithText("✓").assertIsDisplayed()
            chip.performClick()
            chip.assert(stateIs("Excluded"))
            onNodeWithText("✕").assertIsDisplayed()
        }
    }

    @Test
    fun toggleIconButtonRenamesItselfWithItsState() {
        runComposeUiTest {
            setContent {
                var on by remember { mutableStateOf(false) }
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    ToggleIconButton(on, { on = !on }, "Add to library", "Remove from library")
                }
            }
            onNodeWithContentDescription("Add to library").assert(stateIs("Off")).performClick()
            onNodeWithContentDescription("Remove from library").assert(stateIs("On"))
        }
    }

    @Test
    fun tabsSelectByClick() {
        runComposeUiTest {
            setContent {
                var index by remember { mutableIntStateOf(0) }
                BeakoTheme { Tabs(listOf("General", "Accounts", "Sources"), index, { index = it }) }
            }
            onNodeWithText("Accounts").performClick()
            onNodeWithText("Accounts").assertIsSelected()
        }
    }

    @Test
    fun morphSelectOpensAndPicks() {
        runComposeUiTest {
            var picked = -1
            setContent {
                var index by remember { mutableIntStateOf(0) }
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    MorphSelect(
                        listOf("Ascending", "Descending"),
                        index,
                        {
                            index = it
                            picked = it
                        },
                        label = "Sort order",
                    )
                }
            }
            onNodeWithContentDescription("Sort order: Ascending").performClick()
            onNodeWithContentDescription("Descending").performClick()
            waitForIdle()
            assertEquals(1, picked)
            onNodeWithContentDescription("Sort order: Descending").assertIsDisplayed()
        }
    }

    @Test
    fun iconToggleGroupSelectsByName() {
        runComposeUiTest {
            setContent {
                var index by remember { mutableIntStateOf(0) }
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    IconToggleGroup(
                        listOf(
                            IconOption(Icons.Outlined.FitScreen, "Fit screen"),
                            IconOption(Icons.Outlined.Height, "Fit height"),
                        ),
                        index,
                        { index = it },
                        label = "Page fit",
                    )
                }
            }
            onNodeWithContentDescription("Fit height").performClick()
            onNodeWithContentDescription("Fit height").assertIsSelected()
        }
    }

    @Test
    fun arrowButtonAndAlertRender() {
        runComposeUiTest {
            var clicks = 0
            setContent {
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    androidx.compose.foundation.layout.Column {
                        ArrowButton("Continue reading", { clicks++ }, style = ArrowStyle.ACCENT)
                        Alert("2 downloads failed.", kind = AlertKind.ACCENT)
                    }
                }
            }
            onNodeWithText("Continue reading").performClick()
            assertEquals(1, clicks)
            onNodeWithText("2 downloads failed.").assertIsDisplayed()
        }
    }

    @Test
    fun readerInkFollowsItsOwnBackground() {
        // Porcelain's dark ink would vanish on a black page background, and the reverse on white.
        assertEquals(Palette.NEUTRAL, readerPalette("BLACK", Palette.PORCELAIN))
        assertEquals(Palette.NEUTRAL, readerPalette("GRAY", Palette.PORCELAIN))
        assertEquals(null, readerPalette("WHITE", Palette.PORCELAIN))
        assertEquals(Palette.PORCELAIN, readerPalette("WHITE", Palette.MIDNIGHT))
        assertEquals(null, readerPalette("BLACK", Palette.MIDNIGHT))
    }
}
