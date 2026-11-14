package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.io.File
import java.time.LocalDate
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.state.BackupModel
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.backup.BeakoBackup
import org.nekomanga.shared.library.SettingsStore

/** More → Backup and restore. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSection(model: BackupModel, settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsState()
    Section("Backup and restore") {
        Text(
            "Backups hold your library, read chapters, categories and settings. Sign-ins aren’t included. " +
                "You can also restore a backup from Neko on Android: its MangaDex titles, read chapters and categories come across.",
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassButton(
                "Create backup…",
                onClick = {
                    chooseFile(
                            "Save a Beako backup",
                            FileDialog.SAVE,
                            "beako-${LocalDate.now()}.${BeakoBackup.EXTENSION}",
                        )
                        ?.let(model::create)
                },
                icon = Icons.Outlined.Save,
                primary = true,
            )
            GlassButton(
                "Restore from file…",
                onClick = {
                    chooseFile("Restore a Beako or Neko backup", FileDialog.LOAD)
                        ?.let(model::restore)
                },
                icon = Icons.Outlined.Restore,
            )
            GlassButton(
                "Open automatic backups",
                onClick = { openFolder(model.folder) },
                icon = Icons.Outlined.FolderOpen,
            )
        }
        when {
            model.busy ->
                Text(
                    "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            model.message != null ->
                Text(
                    model.message!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.isError) BeakoColors.Danger else BeakoColors.TextMuted,
                )
        }
        ToggleRow(
            "Back up automatically",
            "Once a day while Beako is open, keeping the last 7 days.",
            settings.autoBackup,
        ) {
            settingsStore.update { s -> s.copy(autoBackup = it) }
        }
    }
}

/** The system file dialog; null when cancelled. */
private fun chooseFile(title: String, mode: Int, suggested: String? = null): File? {
    val dialog = FileDialog(null as java.awt.Frame?, title, mode)
    if (suggested != null) dialog.file = suggested
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
