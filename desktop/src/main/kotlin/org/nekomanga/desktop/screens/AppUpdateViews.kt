package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.state.AppUpdateModel
import org.nekomanga.desktop.state.AppUpdateState
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.GlassSurface
import org.nekomanga.desktop.theme.Radius
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.update.AppRelease

/**
 * The app-wide notice when a newer Beako is out: what's new, one button to update, or later. While
 * updating it shows the download's progress, then that Beako is closing to install.
 */
@Composable
fun UpdateNotice(
    model: AppUpdateModel,
    openBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val release = model.notice ?: return
    GlassSurface(RoundedCornerShape(Radius.panel), modifier.width(360.dp)) {
        Column(
            Modifier.padding(18.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Beako ${release.version} is available",
                style = MaterialTheme.typography.titleMedium,
                color = BeakoColors.Text,
            )
            UpdateBody(model, release, openBrowser, showLater = true)
        }
    }
}

/** More › General › About: the version, a check button and the automatic-check switch. */
@Composable
fun AppUpdateSection(
    model: AppUpdateModel,
    settingsStore: SettingsStore,
    openBrowser: (String) -> Unit,
) {
    val settings by settingsStore.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val state = model.state) {
            is AppUpdateState.Available -> {
                Text(
                    "Beako ${state.release.version} is available. You have ${model.currentVersion}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.Text,
                )
                UpdateBody(model, state.release, openBrowser, showLater = false)
            }
            is AppUpdateState.Downloading,
            is AppUpdateState.Installing ->
                UpdateBody(model, (model.notice ?: return@Column), openBrowser, showLater = false)
            else -> {
                Text(
                    when (state) {
                        AppUpdateState.Checking -> "Checking for updates…"
                        AppUpdateState.UpToDate ->
                            "Beako ${model.currentVersion} is the latest version."
                        is AppUpdateState.Failed -> state.message
                        else -> "You have Beako ${model.currentVersion}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (state is AppUpdateState.Failed) BeakoColors.Danger
                        else BeakoColors.TextMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                GlassButton(
                    "Check for updates",
                    { model.check() },
                    enabled = state != AppUpdateState.Checking,
                )
            }
        }
        ToggleRow(
            "Check for updates automatically",
            "Look for a new Beako on GitHub when it starts and twice a day. Nothing installs until you choose Update now.",
            settings.checkAppUpdates,
        ) {
            settingsStore.update { s -> s.copy(checkAppUpdates = it) }
        }
    }
}

@Composable
private fun UpdateBody(
    model: AppUpdateModel,
    release: AppRelease,
    openBrowser: (String) -> Unit,
    showLater: Boolean,
) {
    when (val state = model.state) {
        is AppUpdateState.Downloading -> {
            Text(
                "Downloading… ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = BeakoColors.TextMuted,
            )
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = BeakoColors.Accent,
                trackColor = BeakoColors.ChipRim,
            )
        }
        is AppUpdateState.Installing ->
            Text(
                "Beako will close, install the update and open again. Windows may ask for permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = BeakoColors.TextMuted,
            )
        else -> {
            if (state is AppUpdateState.Failed) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.Danger,
                )
            } else if (release.notes.isNotBlank()) {
                Text(
                    summary(release.notes),
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                    maxLines = 4,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One-click install needs an installed copy and a checksum to check the download.
                if (model.canInstall() && release.checksumUrl != null) {
                    GlassButton(
                        if (state is AppUpdateState.Failed) "Try again" else "Update now",
                        { model.update(release) },
                        primary = true,
                    )
                } else {
                    // Not an installed copy (run from source): the installer is on GitHub.
                    GlassButton("Download", { openBrowser(release.pageUrl) }, primary = true)
                }
                GlassButton("What’s new", { openBrowser(release.pageUrl) })
                if (showLater) GlassButton("Later", model::dismiss)
            }
        }
    }
}

/**
 * A few lines from the release notes for the notice: the bullet points (what's new), with Markdown
 * emphasis and links reduced to their text. Notes without bullets show their first plain lines.
 */
internal fun summary(notes: String): String {
    val lines = notes.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val bullets = lines.filter { it.startsWith("- ") || it.startsWith("* ") }.map { it.drop(2) }
    val picked = bullets.ifEmpty {
        lines.filter { !it.startsWith("#") && !it.startsWith("**Install") }
    }
    return picked.take(4).joinToString("\n") {
        it.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1").replace("**", "").replace("`", "")
    }
}
