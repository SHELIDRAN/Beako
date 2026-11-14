package org.nekomanga.desktop.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.state.TitleTracking
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.tracking.Tracker

/** The title page's tracker buttons: one per signed-in tracker, linked or not. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackingPanel(tracking: TitleTracking, entry: LibraryEntry?) {
    val signedIn =
        tracking.accounts.trackers.filter {
            it.account.collectAsState().value != null || it.isConnected
        }
    if (signedIn.isEmpty()) {
        Text(
            if (ANILIST_ENABLED) "Connect Comick or AniList in More to track this title."
            else "Connect Comick in More to track this title.",
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            signedIn.forEach { tracker ->
                TrackerButton(tracking, tracker, entry?.tracks?.get(tracker.service))
            }
        }
        signedIn.forEach { tracker ->
            tracking.ui[tracker.service]?.error?.let {
                Text(
                    "${tracker.displayName}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.Danger,
                )
            }
        }
        tracking.accounts.syncProblem?.let {
            Text(
                "Last sync didn’t go through: $it",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.Danger,
            )
        }
    }
}

@Composable
private fun TrackerButton(tracking: TitleTracking, tracker: Tracker, track: Track?) {
    val service = tracker.service
    val ui = tracking.ui[service]
    var menu by remember { mutableStateOf(false) }
    Box {
        GlassButton(
            when {
                ui?.busy == true -> "${tracker.displayName} · working…"
                track == null -> "Track on ${tracker.displayName}"
                else -> trackLabel(tracker.displayName, track)
            },
            onClick = {
                when {
                    ui?.busy == true -> Unit
                    track == null -> tracking.autoLink(service)
                    else -> menu = true
                }
            },
            icon = Icons.Outlined.Sync,
        )
        DropdownMenu(menu && track != null, onDismissRequest = { menu = false }) {
            MenuLabel("Status")
            TrackStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = {
                        Text((if (status == track?.status) "✓  " else "     ") + status.label)
                    },
                    onClick = {
                        tracking.setStatus(service, status)
                        menu = false
                    },
                )
            }
            HorizontalDivider(color = BeakoColors.Rim)
            MenuLabel("Score")
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                (1..10).forEach { score ->
                    Text(
                        "$score",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (score == track?.score) BeakoColors.Gold else BeakoColors.Text,
                        modifier =
                            Modifier.clickableLabel("Score $score") {
                                    tracking.setScore(service, score)
                                    menu = false
                                }
                                .padding(4.dp),
                    )
                }
            }
            HorizontalDivider(color = BeakoColors.Rim)
            DropdownMenuItem(
                text = { Text("Change linked title…") },
                onClick = {
                    menu = false
                    tracking.openPicker(service)
                },
            )
            DropdownMenuItem(
                text = { Text("Stop tracking") },
                onClick = {
                    menu = false
                    tracking.unlink(service, removeRemote = false)
                },
            )
            DropdownMenuItem(
                text = {
                    Text("Remove from ${tracker.displayName} list", color = BeakoColors.Danger)
                },
                onClick = {
                    menu = false
                    tracking.unlink(service, removeRemote = true)
                },
            )
        }
        val matches = ui?.matches
        DropdownMenu(matches != null, onDismissRequest = { tracking.closePicker(service) }) {
            var query by remember { mutableStateOf("") }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    query,
                    {
                        query = it
                        tracking.search(service, it)
                    },
                    "Search ${tracker.displayName}",
                    Modifier.width(320.dp),
                )
            }
            when {
                ui?.busy == true -> MenuLabel("Searching…")
                matches.isNullOrEmpty() -> MenuLabel("No matches. Try another name.")
            }
            matches.orEmpty().forEach { match ->
                DropdownMenuItem(
                    text = {
                        Text(listOfNotNull(match.title, match.year?.toString()).joinToString(" · "))
                    },
                    onClick = { tracking.pick(service, match) },
                )
            }
        }
    }
}

/** "Comick · Reading · Ch. 12 · ★ 8". */
internal fun trackLabel(name: String, track: Track): String =
    listOfNotNull(
            name,
            track.status?.label,
            track.progress?.let { "Ch. $it" },
            track.score?.let { "★ $it" },
        )
        .joinToString(" · ")

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = BeakoColors.TextMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun Modifier.clickableLabel(label: String, onClick: () -> Unit): Modifier =
    clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
