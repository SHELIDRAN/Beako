package org.nekomanga.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import org.nekomanga.desktop.components.Cover
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.state.UpdateChecker
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title

private data class UpdateRow(
    val title: Title,
    val chapter: Chapter,
    val day: String,
    val time: Long,
)

private fun publishedMillis(chapter: Chapter, fallback: Long): Long = runCatching {
    OffsetDateTime.parse(chapter.publishedAt).toInstant().toEpochMilli()
}
    .getOrDefault(fallback)

/** New chapters of library titles, newest first and grouped by day, with a manual check. */
@Composable
fun UpdatesScreen(
    store: LibraryStore,
    checker: UpdateChecker,
    onOpen: (Title) -> Unit,
    onLibrary: () -> Unit,
) {
    val data by store.data.collectAsState()
    val rows =
        remember(data) {
            data.entries
                .flatMap { entry ->
                    entry.newChapters.map {
                        val time = publishedMillis(it.chapter, it.foundAt)
                        UpdateRow(entry.title, it.chapter, dayLabel(time), time)
                    }
                }
                .sortedByDescending { it.time }
        }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item("header") {
            Column(
                Modifier.padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Latest updates",
                        style = MaterialTheme.typography.displaySmall,
                        color = BeakoColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        if (checker.running) "Checking…" else "Check for updates",
                        onClick = { checker.runNow() },
                        icon = Icons.Outlined.Refresh,
                    )
                }
                val (done, total) = checker.progress
                val status =
                    when {
                        checker.running -> "Checking $done of $total titles…"
                        checker.lastRunAt != null && checker.failures > 0 ->
                            "Checked just now · ${checker.failures} title(s) couldn’t be checked"
                        checker.lastRunAt != null -> "Checked just now"
                        else ->
                            "Beako checks your library for new chapters every few hours while it’s open."
                    }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.TextMuted,
                )
            }
        }
        when {
            data.entries.isEmpty() ->
                item("nolib") {
                    StateMessage(
                        "No titles to check",
                        "Add titles to your library to see their new chapters here.",
                    ) {
                        GlassButton("Open Library", onClick = onLibrary, primary = true)
                    }
                }
            rows.isEmpty() ->
                item("none") {
                    StateMessage(
                        "You’re all caught up",
                        "New chapters of your library titles appear here.",
                    )
                }
            else ->
                rows
                    .groupBy { it.day }
                    .forEach { (day, dayRows) ->
                        item("d:$day") {
                            Text(
                                day,
                                style = MaterialTheme.typography.titleLarge,
                                color = BeakoColors.Text,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            dayRows,
                            key = { "${it.title.source}:${it.title.id}:${it.chapter.id}" },
                        ) { row ->
                            UpdateRowView(row, onClick = { onOpen(row.title) })
                        }
                    }
        }
    }
}

@Composable
private fun UpdateRowView(row: UpdateRow, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Color(0x12FFFFFF) else Color.Transparent)
            .hoverable(hover)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${row.title.title}, ${row.chapter.displayName()}, unread"
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(
            row.title.coverUrl,
            Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.title.title,
                style = MaterialTheme.typography.titleMedium,
                color = BeakoColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(row.chapter.displayName(), row.chapter.group, "MangaDex")
                    .joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
        // Unread dot.
        Box(Modifier.size(9.dp).clip(CircleShape).background(BeakoColors.Text))
    }
}
