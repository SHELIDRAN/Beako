package org.nekomanga.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Explore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.nekomanga.desktop.components.Cover
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.HistoryEntry
import org.nekomanga.shared.library.LibraryStore

/** "Today", "Yesterday" or a date, for grouping lists by day. */
fun dayLabel(millis: Long, today: LocalDate = LocalDate.now()): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
    }
}

/** Reading history grouped by day, newest first, with resume and remove. */
@Composable
fun HistoryScreen(store: LibraryStore, onResume: (HistoryEntry) -> Unit, onBrowse: () -> Unit) {
    val data by store.data.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val byDay = remember(data.history) { data.history.groupBy { dayLabel(it.readAt) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item("header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Text(
                    "Reading history",
                    style = MaterialTheme.typography.displaySmall,
                    color = BeakoColors.Text,
                    modifier = Modifier.weight(1f),
                )
                if (data.history.isNotEmpty()) {
                    if (confirmClear) {
                        Text("Clear all history?", color = BeakoColors.TextMuted)
                        GlassButton(
                            "Clear",
                            onClick = {
                                store.clearHistory()
                                confirmClear = false
                            },
                            primary = true,
                        )
                        GlassButton("Cancel", onClick = { confirmClear = false })
                    } else {
                        GlassButton(
                            "Clear history",
                            onClick = { confirmClear = true },
                            icon = Icons.Outlined.DeleteSweep,
                        )
                    }
                }
            }
        }
        if (data.history.isEmpty()) {
            item("empty") {
                StateMessage(
                    "Nothing read yet",
                    "Chapters you open appear here so you can pick up where you left off.",
                ) {
                    GlassButton(
                        "Browse",
                        onClick = onBrowse,
                        icon = Icons.Outlined.Explore,
                        primary = true,
                    )
                }
            }
        }
        byDay.forEach { (day, entries) ->
            item("d:$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.titleLarge,
                    color = BeakoColors.Text,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            items(entries, key = { "${it.title.source}:${it.title.id}" }) { entry ->
                HistoryRow(
                    entry,
                    onResume = { onResume(entry) },
                    onRemove = { store.removeFromHistory(entry.title) },
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onResume: () -> Unit, onRemove: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val time =
        Instant.ofEpochMilli(entry.readAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Color(0x12FFFFFF) else Color.Transparent)
            .hoverable(hover)
            .clickable(role = Role.Button, onClickLabel = "Resume", onClick = onResume)
            .semantics(mergeDescendants = true) {
                contentDescription = "Resume ${entry.title.title}"
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(
            entry.title.coverUrl,
            Modifier.size(width = 56.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.title.title,
                style = MaterialTheme.typography.titleMedium,
                color = BeakoColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.chapter.displayName()} · page ${entry.page + 1} · $time",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
        GlassButton("Resume", onClick = onResume)
        GlassIconButton(Icons.Outlined.Close, "Remove from history", onRemove)
    }
}
