package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassTextField
import org.nekomanga.desktop.state.DISCORD_APP_ID
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.SettingsStore

/** More → Discord: Rich Presence while reading. */
@Composable
fun DiscordSection(settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsState()
    Section("Discord") {
        ToggleRow(
            "Show what I’m reading in Discord",
            "Uses the Discord app on this PC. Hidden in incognito mode.",
            settings.discordPresence,
        ) {
            settingsStore.update { s -> s.copy(discordPresence = it) }
        }
        if (settings.discordPresence) {
            ToggleRow(
                "Show the title",
                "Off shows only “Reading manga”.",
                settings.discordShowTitle,
            ) {
                settingsStore.update { s -> s.copy(discordShowTitle = it) }
            }
            if (settings.discordShowTitle) {
                ToggleRow("Show the chapter", "", settings.discordShowChapter) {
                    settingsStore.update { s -> s.copy(discordShowChapter = it) }
                }
                ToggleRow("Show the cover", "", settings.discordShowCover) {
                    settingsStore.update { s -> s.copy(discordShowCover = it) }
                }
            }
            if (DISCORD_APP_ID.isBlank()) {
                Text(
                    "Beako’s Discord app isn’t registered yet: paste a Discord application ID to use this now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
                GlassTextField(
                    "Discord application ID",
                    settings.discordAppId,
                    { value ->
                        settingsStore.update { it.copy(discordAppId = value.filter(Char::isDigit)) }
                    },
                    Modifier.widthIn(max = 320.dp),
                )
            }
        }
    }
}
