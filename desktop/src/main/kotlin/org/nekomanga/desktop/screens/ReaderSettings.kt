package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.state.ReadingMode
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.ReaderPrefs
import org.nekomanga.shared.library.SettingsStore

/** More → Reader: defaults for every title, in General, Display, Reading, Paged and Webtoon. */
@Composable
internal fun ReaderSettings(settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsState()
    val r = settings.reader
    fun set(change: (ReaderPrefs) -> ReaderPrefs) = settingsStore.update {
        it.copy(reader = change(it.reader))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Section("General") {
            LabeledMenu(
                "Default reading mode",
                if (r.defaultMode == "AUTO") "By type (manga right to left, webtoons as a strip)"
                else
                    ReadingMode.entries.firstOrNull { it.name == r.defaultMode }?.label
                        ?: "By type",
                listOf("By type (manga right to left, webtoons as a strip)") +
                    ReadingMode.entries.map { it.label },
            ) { index ->
                set {
                    it.copy(
                        defaultMode =
                            if (index == 0) "AUTO" else ReadingMode.entries[index - 1].name,
                    )
                }
            }
            Text(
                "A title keeps the mode you pick for it in the reader.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
            LabeledMenu(
                "Pages to load ahead",
                "${r.preload}",
                (1..10).map { "$it" },
            ) { index ->
                set { it.copy(preload = index + 1) }
            }
            Text(
                "More pages ahead makes reading smoother on a slow connection, and uses more data.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }

        Section("Display") {
            ChoiceRow(
                "Background",
                listOf("BLACK" to "Black", "GRAY" to "Gray", "WHITE" to "White"),
                r.background,
                optionWidth = 72,
            ) { value ->
                set { it.copy(background = value) }
            }
            ToggleRow(
                "Open in full screen",
                "Start every chapter in full screen.",
                r.openFullscreen,
            ) { on ->
                set { it.copy(openFullscreen = on) }
            }
            ToggleRow(
                "Keep screen on",
                "Stop Windows dimming or turning off the display while the reader is open.",
                r.keepScreenOn,
            ) { on ->
                set { it.copy(keepScreenOn = on) }
            }
            ToggleRow(
                "Show page number",
                "A small page number while the controls are hidden.",
                r.showPageNumber,
            ) { on ->
                set { it.copy(showPageNumber = on) }
            }
            ToggleRow("Grayscale", "Show pages in grey.", r.grayscale) { on ->
                set { it.copy(grayscale = on) }
            }
            ToggleRow(
                "Crop borders",
                "Trim plain white or black margins around pages.",
                r.cropBorders,
            ) { on ->
                set { it.copy(cropBorders = on) }
            }
        }

        Section("Reading") {
            ToggleRow(
                "Skip chapters marked read",
                "Next chapter jumps over chapters you’ve already read.",
                r.skipRead,
            ) { on ->
                set { it.copy(skipRead = on) }
            }
            ToggleRow(
                "Skip duplicate chapters",
                "When several groups uploaded a chapter, read just one, keeping the group you read before.",
                r.skipDuplicates,
            ) { on ->
                set { it.copy(skipDuplicates = on) }
            }
            Text(
                "Chapters from groups you’ve hidden on a title page are always skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
            ToggleRow(
                "Always show chapter transition",
                "In page modes, a page between chapters shows what you finished and what’s next.",
                r.chapterTransition,
            ) { on ->
                set { it.copy(chapterTransition = on) }
            }
        }

        Section("Paged") {
            ChoiceRow(
                "Page layout",
                listOf("SINGLE" to "Single", "DOUBLE" to "Two pages", "AUTO" to "Automatic"),
                r.pageLayout,
                optionWidth = 96,
            ) { value ->
                set { it.copy(pageLayout = value) }
            }
            Text(
                "Automatic shows two pages side by side when the window is wider than tall. Double-page " +
                    "spreads always show alone.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
            ToggleRow(
                "Invert two-page order",
                "Swap which page of a pair is on the left.",
                r.invertDouble,
            ) { on ->
                set { it.copy(invertDouble = on) }
            }
            LabeledMenu(
                "Gap between pages",
                if (r.doubleGap == 0) "None" else "${r.doubleGap} px",
                listOf("None", "8 px", "16 px", "32 px"),
            ) { index ->
                set { it.copy(doubleGap = listOf(0, 8, 16, 32)[index]) }
            }
            ToggleRow(
                "Split wide pages",
                "Show double-page spreads one half at a time.",
                r.splitWidePages,
            ) { on ->
                set { it.copy(splitWidePages = on) }
            }
            ToggleRow("Animate page turns", "Slide from one page to the next.", r.animateTurns) { on
                ->
                set { it.copy(animateTurns = on) }
            }
            LabeledMenu(
                "Click zones",
                TapZones.firstOrNull { it.first == r.tapZones }?.second ?: "Default",
                TapZones.map { it.second },
            ) { index ->
                set { it.copy(tapZones = TapZones[index].first) }
            }
            ToggleRow(
                "Swap click sides",
                "Click the left side to go forward in left-to-right mode, and the reverse.",
                r.invertClickZones,
            ) { on ->
                set { it.copy(invertClickZones = on) }
            }
        }

        Section("Webtoon") {
            ChoiceRow(
                "Hide controls on scroll",
                listOf("LOW" to "Low", "MEDIUM" to "Medium", "HIGH" to "High"),
                r.hideOnScroll,
                optionWidth = 80,
            ) { value ->
                set { it.copy(hideOnScroll = value) }
            }
            Text(
                "How quickly scrolling down hides the controls. Clicks follow the click zones above, top " +
                    "to bottom: the top part scrolls back, the bottom part on.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
            ToggleRow(
                "Detect webtoons",
                "Switch to Webtoon when a series’ pages are long strips, unless you picked a mode for it.",
                r.autoWebtoon,
            ) { on ->
                set { it.copy(autoWebtoon = on) }
            }
        }
    }
}

private val TapZones =
    listOf(
        "DEFAULT" to "Default (sides turn, middle for controls)",
        "KINDLE" to "Kindle (left back, the rest forward)",
        "EDGE" to "Edges (narrow sides turn)",
        "L_SHAPE" to "L-shaped",
        "DISABLED" to "Off (clicks show controls)",
    )
