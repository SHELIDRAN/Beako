package org.nekomanga.desktop

import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
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
import org.nekomanga.desktop.theme.WindowsAccent

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
            Palette.entries.forEach(::assertReadable)
        } finally {
            BeakoColors.palette = before
        }
    }

    @Test
    fun dynamicSchemesStayReadableWhateverTheWindowsAccent() {
        val before = BeakoColors.palette
        try {
            for (rgb in
                listOf(
                    0x0078D4, // Windows blue
                    0xFFD800, // pale yellow
                    0x001F5C, // near-black navy
                    0xFF0000,
                    0x00CC6A,
                    0xE3008C,
                    0x767676,
                    0xFFFFFF,
                    0x000000,
                )) {
                WindowsAccent.update(Color(0xFF000000 or rgb.toLong()))
                assertReadable(Palette.DYNAMIC_DARK)
                assertReadable(Palette.DYNAMIC_LIGHT)
            }
            // The scheme follows the accent: a red accent gives a red-ish accent colour.
            WindowsAccent.update(Color(0xFFE81123))
            BeakoColors.palette = Palette.DYNAMIC_DARK
            assertTrue(BeakoColors.Accent.red > BeakoColors.Accent.blue, "red accent stays red")
        } finally {
            WindowsAccent.update(WindowsAccent.DEFAULT)
            BeakoColors.palette = before
        }
    }

    @Test
    fun windowsAccentColoursAreReadInTheRightOrder() {
        // Windows stores 0xAABBGGRR: its default blue #0078D4 is 0xFFD47800.
        assertEquals(0x0078D4, org.nekomanga.desktop.platform.abgrToRgb(0xFFD47800.toInt()))
    }

    /** Text 7:1, secondary text, error, rating and success 4.5:1, accent 3:1, labels 4.5:1. */
    private fun assertReadable(palette: Palette) {
        BeakoColors.palette = palette
        for (bg in
            listOf(
                BeakoColors.Ground,
                BeakoColors.Surface,
                BeakoColors.SurfaceRaised,
                BeakoColors.GlassSolid,
            )) {
            assertTrue(contrast(BeakoColors.Text, bg) >= 7.0, "text on $palette")
            assertTrue(contrast(BeakoColors.TextMuted, bg) >= 4.5, "muted on $palette")
            assertTrue(contrast(BeakoColors.Danger, bg) >= 4.5, "danger on $palette")
            assertTrue(contrast(BeakoColors.Gold, bg) >= 4.5, "rating on $palette")
            assertTrue(contrast(BeakoColors.Positive, bg) >= 4.5, "matched on $palette")
        }
        assertTrue(contrast(BeakoColors.Accent, BeakoColors.Ground) >= 3.0, "accent $palette")
        assertTrue(
            contrast(BeakoColors.AccentInk, BeakoColors.AccentFill) >= 4.5,
            "accent button on $palette",
        )
        assertTrue(contrast(BeakoColors.Ground, BeakoColors.Text) >= 7.0)
        assertTrue(contrast(BeakoColors.InkOnFill, BeakoColors.InkFill) >= 7.0)
    }

    @Test
    fun lightSchemesFlipTheInkAndBeakosOwnKeepTheOrangeAccent() {
        val before = BeakoColors.palette
        try {
            for (palette in Palette.entries) {
                BeakoColors.palette = palette
                if (palette.light) {
                    assertTrue(BeakoColors.Text.luminance() < 0.2f, "dark text on $palette")
                } else {
                    assertTrue(BeakoColors.Text.luminance() > 0.5f, "light text on $palette")
                }
            }
            for (own in listOf(Palette.NEUTRAL, Palette.PORCELAIN, Palette.OLED)) {
                BeakoColors.palette = own
                assertEquals(Color(0xFFDF5830), BeakoColors.Accent)
            }
            assertEquals(Palette.PORCELAIN, Palette.of("PORCELAIN"))
            assertEquals(Palette.NEUTRAL, Palette.of("NOT_A_SCHEME"))
            assertTrue(Palette.entries.count { it.fromNeko } >= 20)
            assertEquals(2, Palette.entries.count { it.dynamic })
        } finally {
            BeakoColors.palette = before
        }
    }

    @Test
    fun followingWindowsPicksTheLightOrDarkScheme() {
        fun effective(follow: Boolean, light: Boolean?) =
            Palette.effective("NORD_DARK", "KITSUNE_FIRE_LIGHT", "NEON_NIGHTS_DARK", follow, light)
        assertEquals(Palette.NORD_DARK, effective(follow = false, light = true))
        assertEquals(Palette.KITSUNE_FIRE_LIGHT, effective(follow = true, light = true))
        assertEquals(Palette.NEON_NIGHTS_DARK, effective(follow = true, light = false))
        // Windows' mode unknown (off Windows): the scheme picked last.
        assertEquals(Palette.NORD_DARK, effective(follow = true, light = null))
        // A scheme saved in the wrong slot falls back to Porcelain or Neutral.
        assertEquals(
            Palette.PORCELAIN,
            Palette.effective("NEUTRAL", "NORD_DARK", "NEUTRAL", true, true),
        )
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

    @Test
    fun ctrlFFocusesTheShownSearchBoxElseItIsMissing() {
        runComposeUiTest {
            val shortcut = org.nekomanga.desktop.state.SearchShortcut()
            assertEquals(false, shortcut.focus())
            var show by mutableStateOf(true)
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    org.nekomanga.desktop.state.LocalSearchShortcut provides shortcut,
                ) {
                    BeakoTheme {
                        if (show) {
                            org.nekomanga.desktop.components.SoftField(
                                "",
                                {},
                                "Search library",
                                focusRequester =
                                    org.nekomanga.desktop.state.rememberSearchShortcut(),
                            )
                        }
                    }
                }
            }
            runOnIdle { assertEquals(true, shortcut.focus()) }
            onNodeWithContentDescription("Search library").assertIsFocused()
            show = false
            runOnIdle { assertEquals(false, shortcut.focus()) }
        }
    }

    @Test
    fun iconButtonsKeepTheirNameAndAFocusRingDoesNotBreakThem() {
        runComposeUiTest {
            var clicks = 0
            setContent {
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    org.nekomanga.desktop.components.GlassIconButton(
                        Icons.Outlined.Height,
                        "Next chapter",
                        { clicks++ },
                        swingFrom = 90f,
                    )
                }
            }
            onNodeWithContentDescription("Next chapter").performClick()
            assertEquals(1, clicks)
        }
    }

    @Test
    fun arrowButtonKeepsItsSizeInAWideRow() {
        runComposeUiTest {
            setContent {
                BeakoTheme(BeakoAppearance(reduceMotion = true)) {
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.width(1200.dp),
                    ) {
                        ArrowButton("Start reading", {}, style = ArrowStyle.ACCENT)
                        androidx.compose.material3.Text("Download unread")
                    }
                }
            }
            // 0.1.4 stretched the button across the row and pushed its neighbours out of view.
            onNodeWithText("Download unread").assertIsDisplayed()
            val width =
                onNodeWithText("Start reading", useUnmergedTree = true).fetchSemanticsNode().let {
                    node ->
                    node.parent?.size?.width ?: 0
                }
            assertTrue(width in 1..400, "button width $width px")
        }
    }
}
