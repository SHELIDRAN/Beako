package org.nekomanga.desktop.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.IconOption
import org.nekomanga.desktop.components.IconToggleGroup
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.library.ReaderPrefs

/** The reader's settings, in a glass panel on the right. Changes apply at once and are saved. */
@Composable
fun BoxScope.ReaderSettingsPanel(
    visible: Boolean,
    prefs: ReaderPrefs,
    paged: Boolean,
    onPrefs: (ReaderPrefs) -> Unit,
    onClose: () -> Unit,
) {
    val reduceMotion = LocalAppearance.current.reduceMotion
    AnimatedVisibility(
        visible,
        Modifier.align(Alignment.CenterEnd),
        enter =
            if (reduceMotion) fadeIn(snap())
            else fadeIn(tween(180)) + slideInHorizontally { it / 4 },
        exit =
            if (reduceMotion) fadeOut(snap())
            else fadeOut(tween(140)) + slideOutHorizontally { it / 4 },
    ) {
        Column(
            Modifier.padding(12.dp)
                .width(340.dp)
                .fillMaxHeight()
                .glass(RoundedCornerShape(22.dp), tint = BeakoColors.GlassSolid.copy(alpha = 0.88f))
                // Clicks on the panel's empty space must not reach the page-turn zones beneath.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Reader settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = BeakoColors.Text,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(Icons.Outlined.Close, "Close reader settings (Esc)", onClose)
            }
            val backgrounds = listOf("BLACK" to "Black", "GRAY" to "Gray", "WHITE" to "White")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Background",
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                )
                DropletSegmented(
                    backgrounds.map { it.second },
                    backgrounds.indexOfFirst { it.first == prefs.background }.coerceAtLeast(0),
                    { onPrefs(prefs.copy(background = backgrounds[it].first)) },
                    label = "Background",
                )
            }
            if (paged) {
                val fit = PageFit.of(prefs.fit)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Page fit",
                            style = MaterialTheme.typography.titleMedium,
                            color = BeakoColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            fit.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BeakoColors.TextMuted,
                        )
                    }
                    IconToggleGroup(
                        PageFit.entries.map { IconOption(it.icon, it.label) },
                        fit.ordinal,
                        { onPrefs(prefs.copy(fit = PageFit.entries[it].name)) },
                        label = "Page fit",
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Page layout",
                        style = MaterialTheme.typography.titleMedium,
                        color = BeakoColors.Text,
                    )
                    val layouts = listOf("SINGLE" to "Single", "DOUBLE" to "Two", "AUTO" to "Auto")
                    DropletSegmented(
                        layouts.map { it.second },
                        layouts.indexOfFirst { it.first == prefs.pageLayout }.coerceAtLeast(0),
                        { onPrefs(prefs.copy(pageLayout = layouts[it].first)) },
                        label = "Page layout",
                    )
                }
                ToggleRow(
                    "Split wide pages",
                    "Show double-page spreads one half at a time.",
                    prefs.splitWidePages,
                ) {
                    onPrefs(prefs.copy(splitWidePages = it))
                }
                ToggleRow(
                    "Swap click sides",
                    "Click the left side to go forward in left-to-right mode, and the reverse.",
                    prefs.invertClickZones,
                ) {
                    onPrefs(prefs.copy(invertClickZones = it))
                }
            } else {
                Setting("Strip width", "${prefs.webtoonWidth}%", prefs.webtoonWidth, 30..100, 13) {
                    onPrefs(prefs.copy(webtoonWidth = it))
                }
                Setting("Gap between pages", "${prefs.pageGap} px", prefs.pageGap, 0..32, 7) {
                    onPrefs(prefs.copy(pageGap = it))
                }
            }
            ToggleRow(
                "Crop borders",
                "Trim plain white or black margins around pages.",
                prefs.cropBorders,
            ) {
                onPrefs(prefs.copy(cropBorders = it))
            }
            ToggleRow("Grayscale", "Show pages in grey.", prefs.grayscale) {
                onPrefs(prefs.copy(grayscale = it))
            }
            ToggleRow(
                "Detect webtoons",
                "Switch to Webtoon when a series’ pages are long strips, unless you picked a mode for it.",
                prefs.autoWebtoon,
            ) {
                onPrefs(prefs.copy(autoWebtoon = it))
            }
            Text(
                "Beako remembers the reading mode you pick for each series. More options are in " +
                    "More → Reader.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
    }
}

/** A slider that previews while dragging and saves when released. */
@Composable
private fun Setting(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    steps: Int,
    onChange: (Int) -> Unit,
) {
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = BeakoColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = BeakoColors.TextMuted,
            )
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(dragging.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            colors =
                SliderDefaults.colors(
                    thumbColor = BeakoColors.Text,
                    activeTrackColor = BeakoColors.Text,
                    inactiveTrackColor = BeakoColors.Rim,
                ),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
