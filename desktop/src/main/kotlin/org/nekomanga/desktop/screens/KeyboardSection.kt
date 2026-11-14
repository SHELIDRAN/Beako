package org.nekomanga.desktop.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.state.KeyBindings
import org.nekomanga.desktop.state.ReaderAction
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.library.SettingsStore

/** More → Keyboard shortcuts: the reader's keys, each removable, with "Add key" to rebind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardSection(settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsState()
    val bindings = remember(settings.keyBindings) { KeyBindings(settings.keyBindings) }
    var capturing by remember { mutableStateOf<ReaderAction?>(null) }

    Section("Keyboard shortcuts") {
        Text(
            "Keys for the reader. Adding a key that another action uses moves it here. Esc always closes panels, " +
                "leaves full screen and then closes the reader.",
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
        ReaderAction.entries.forEach { action ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                    modifier = Modifier.width(260.dp),
                )
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    bindings.map[action].orEmpty().forEach { code ->
                        GlassButton(
                            "${KeyBindings.name(code)}  ×",
                            onClick = {
                                settingsStore.update {
                                    it.copy(
                                        keyBindings =
                                            KeyBindings.unbind(it.keyBindings, action, code),
                                    )
                                }
                            },
                        )
                    }
                    if (capturing == action) {
                        KeyCapture(
                            action,
                            onKey = { code ->
                                settingsStore.update {
                                    it.copy(
                                        keyBindings =
                                            KeyBindings.bind(it.keyBindings, action, code),
                                    )
                                }
                                capturing = null
                            },
                            onCancel = { capturing = null },
                        )
                    } else {
                        GlassButton(
                            "Add key",
                            onClick = { capturing = action },
                            icon = Icons.Outlined.Add,
                        )
                    }
                }
            }
        }
        GlassButton(
            "Reset to defaults",
            onClick = { settingsStore.update { it.copy(keyBindings = emptyMap()) } },
        )
    }
}

/** Waits for the next key press; Esc cancels. */
@Composable
private fun KeyCapture(action: ReaderAction, onKey: (Int) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(action) { runCatching { focus.requestFocus() } }
    Text(
        "Press a key… (Esc to cancel)",
        style = MaterialTheme.typography.labelLarge,
        color = BeakoColors.Text,
        modifier =
            Modifier.glass(RoundedCornerShape(50), tint = BeakoColors.RimBright)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.Escape) onCancel() else onKey(event.key.nativeKeyCode)
                    true
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
