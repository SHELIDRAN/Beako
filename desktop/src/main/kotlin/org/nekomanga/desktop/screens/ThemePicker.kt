package org.nekomanga.desktop.screens

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.selectedState
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.Palette
import org.nekomanga.desktop.theme.focusRing
import org.nekomanga.desktop.theme.press
import org.nekomanga.shared.library.Settings

/**
 * More → Appearance → colour scheme: a row of dark schemes and a row of light ones, each shown as a
 * small preview of the app in its colours, and a switch to follow Windows' light or dark mode.
 */
@Composable
internal fun ThemePicker(
    settings: Settings,
    inUse: Palette,
    windowsModeKnown: Boolean,
    onPick: (Palette) -> Unit,
    onFollow: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val chosenDark =
            Palette.of(settings.palette).takeIf { !it.light }
                ?: Palette.of(settings.darkPalette).takeIf { !it.light }
                ?: Palette.NEUTRAL
        val chosenLight =
            Palette.of(settings.palette).takeIf { it.light }
                ?: Palette.of(settings.lightPalette).takeIf { it.light }
                ?: Palette.PORCELAIN
        ThemeRow("Dark schemes", Palette.entries.filter { !it.light }, chosenDark, inUse, onPick)
        ThemeRow("Light schemes", Palette.entries.filter { it.light }, chosenLight, inUse, onPick)
        if (windowsModeKnown) {
            ToggleRow(
                "Follow Windows light or dark mode",
                "Use your light scheme when Windows apps are in light mode, and your dark scheme otherwise.",
                settings.followSystemTheme,
                onFollow,
            )
        }
        Text(
            "Kitsune Fire, Miami Vice and the other named schemes come from Neko on Android.",
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
    }
}

@Composable
private fun ThemeRow(
    title: String,
    palettes: List<Palette>,
    chosen: Palette,
    inUse: Palette,
    onPick: (Palette) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = BeakoColors.Text)
        val list = rememberLazyListState()
        // Open with the chosen scheme in view.
        LaunchedEffect(Unit) { list.scrollToItem((palettes.indexOf(chosen) - 1).coerceAtLeast(0)) }
        LazyRow(
            state = list,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(palettes, key = { it.name }) { palette ->
                ThemeCard(palette, chosen = palette == chosen, inUse = palette == inUse) {
                    onPick(palette)
                }
            }
        }
    }
}

/** A small picture of the app in [palette]: a bar, a card with the accent pill, three dots. */
@Composable
private fun ThemeCard(palette: Palette, chosen: Boolean, inUse: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    val text = palette.text ?: if (palette.light) Color(0xFF17171A) else Color(0xFFF5F5F6)
    val muted = palette.textMuted ?: if (palette.light) Color(0xFF55565E) else Color(0xFFA9A9AF)
    val secondary = palette.secondary ?: palette.accentFill
    Column(
        Modifier.width(112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.press(interaction)
                .size(width = 112.dp, height = 148.dp)
                .focusRing(interaction, shape)
                .clip(shape)
                .background(palette.ground)
                .border(
                    if (chosen) 3.dp else 1.dp,
                    if (chosen) BeakoColors.Accent else BeakoColors.Rim,
                    shape,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = palette.label + if (palette.light) ", light" else ", dark"
                    selectedState(chosen)
                    if (inUse) stateDescription = "In use"
                },
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Top bar.
                Box(
                    Modifier.size(width = 54.dp, height = 12.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceRaised),
                )
                // A card with the accent and second colour as a two-tone pill.
                Box(
                    Modifier.size(width = 60.dp, height = 70.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface)
                        .padding(8.dp),
                ) {
                    Row(Modifier.clip(CircleShape)) {
                        Box(Modifier.size(width = 13.dp, height = 12.dp).background(palette.accent))
                        Box(Modifier.size(width = 13.dp, height = 12.dp).background(secondary))
                    }
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .size(width = 34.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(text.copy(alpha = 0.7f)),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf(muted, palette.accent, muted).forEach {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(it))
                    }
                }
            }
            if (inUse) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(BeakoColors.AccentFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        null,
                        tint = BeakoColors.AccentInk,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            palette.label.removeSuffix(" (light)"),
            style = MaterialTheme.typography.labelMedium,
            color = if (chosen) BeakoColors.Text else BeakoColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
