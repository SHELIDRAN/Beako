package org.nekomanga.desktop.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import org.nekomanga.desktop.components.Cover
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.LoadingMessage
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.components.Tabs
import org.nekomanga.desktop.components.TitleCard
import org.nekomanga.desktop.state.FeedGroup
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.MangaDexModel
import org.nekomanga.desktop.state.MangaDexTab
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.GlassSurface
import org.nekomanga.desktop.theme.Radius
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title

/**
 * Browse › MangaDex: MangaDex's own catalog in tabs (popular new titles, latest updates, recently
 * added, top rated) and, once signed in, the Feed of new chapters from followed titles.
 */
@Composable
fun MangaDexScreen(
    model: MangaDexModel,
    onBack: () -> Unit,
    onOpen: (Title) -> Unit,
    onRead: (Title, Chapter) -> Unit,
    onConnect: () -> Unit,
) {
    LaunchedEffect(model) { model.start() }
    val grid = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 6
        }
    }
    val tab = model.tab
    val loaded = if (tab == MangaDexTab.FEED) model.feed.size else model.titles.size
    LaunchedEffect(nearEnd, loaded, tab) { if (nearEnd && loaded > 0) model.loadMore() }
    LaunchedEffect(tab) { grid.scrollToItem(0) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (tab == MangaDexTab.FEED) 420.dp else 172.dp),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        "Back",
                        onBack,
                        swingFrom = -90f,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "MangaDex",
                            style = MaterialTheme.typography.displaySmall,
                            color = BeakoColors.Text,
                        )
                        Text(
                            "Discover titles on MangaDex, and follow new chapters in your feed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BeakoColors.TextMuted,
                        )
                    }
                    if (model.opening) {
                        Text(
                            "Opening chapter…",
                            style = MaterialTheme.typography.labelLarge,
                            color = BeakoColors.TextMuted,
                        )
                    }
                    GlassIconButton(
                        Icons.Outlined.Refresh,
                        if (tab == MangaDexTab.FEED) "Refresh feed" else "Refresh list",
                        model::reload,
                    )
                }
                Tabs(
                    MangaDexTab.entries.map { it.label },
                    MangaDexTab.entries.indexOf(tab),
                    { model.select(MangaDexTab.entries[it]) },
                )
            }
        }
        if (tab == MangaDexTab.FEED) feed(model, onOpen, onRead, onConnect)
        else catalog(model, onOpen)
        model.pageError?.let { error ->
            item("page-error", span = { GridItemSpan(maxLineSpan) }) {
                StateMessage("Couldn’t load more", error) { GlassButton("Try again", model::retry) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.catalog(
    model: MangaDexModel,
    onOpen: (Title) -> Unit,
) {
    when (val status = model.status) {
        Loadable.Loading ->
            if (model.titles.isEmpty()) {
                item("loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                        LoadingMessage("Loading ${model.tab.label.lowercase()}…")
                    }
                }
            }
        is Loadable.Failed ->
            item("failed", span = { GridItemSpan(maxLineSpan) }) {
                StateMessage("Couldn’t load this list", status.message) {
                    GlassButton("Try again", model::reload)
                }
            }
        is Loadable.Ready ->
            if (model.titles.isEmpty()) {
                item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage(
                        "Nothing here yet",
                        "MangaDex has no titles in this list for your languages.",
                    )
                }
            }
    }
    items(model.titles, key = { "t:" + it.id }) { title ->
        TitleCard(title, onClick = { onOpen(title) })
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.feed(
    model: MangaDexModel,
    onOpen: (Title) -> Unit,
    onRead: (Title, Chapter) -> Unit,
    onConnect: () -> Unit,
) {
    if (!model.signedIn) {
        item("signin", span = { GridItemSpan(maxLineSpan) }) {
            StateMessage(
                "Sign in to see your feed",
                "Your feed lists new chapters of the titles you follow on MangaDex. Connect your MangaDex account in More › Accounts.",
            ) {
                GlassButton("Connect MangaDex", onConnect, primary = true)
            }
        }
        return
    }
    when (val status = model.feedStatus) {
        Loadable.Loading ->
            if (model.feed.isEmpty()) {
                item("loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                        LoadingMessage("Loading your feed…")
                    }
                }
            }
        is Loadable.Failed ->
            item("failed", span = { GridItemSpan(maxLineSpan) }) {
                StateMessage("Couldn’t load your feed", status.message) {
                    GlassButton("Try again", model::reload)
                }
            }
        is Loadable.Ready ->
            if (model.feed.isEmpty()) {
                item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage(
                        "No new chapters",
                        "Titles you follow on MangaDex will show here when they get new chapters in your languages.",
                    )
                }
            }
    }
    items(model.feed, key = { "f:" + it.title.id }) { group ->
        FeedCard(group, { onOpen(group.title) }, { onRead(group.title, it) })
    }
}

/** A followed title with its newest chapters, like a feed post: cover, title, chapter rows. */
@Composable
private fun FeedCard(group: FeedGroup, onOpen: () -> Unit, onRead: (Chapter) -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    GlassSurface(shape, Modifier.fillMaxWidth(), elevation = 0.dp) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(
                group.title.coverUrl,
                Modifier.size(84.dp, 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onOpen)
                    .semantics { contentDescription = "Open ${group.title.title}" },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    group.title.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
                )
                group.chapters.take(MAX_ROWS).forEach { chapter ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onRead(chapter) }
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                chapter.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BeakoColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            chapter.group?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BeakoColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            ago(chapter.publishedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = BeakoColors.TextMuted,
                        )
                    }
                }
                if (group.chapters.size > MAX_ROWS) {
                    Text(
                        "+${group.chapters.size - MAX_ROWS} more",
                        style = MaterialTheme.typography.labelMedium,
                        color = BeakoColors.TextMuted,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_ROWS = 4

/** "5 min ago", "3 h ago", "2 d ago" or the date, from an ISO-8601 time. */
internal fun ago(iso: String, now: Instant = Instant.now()): String {
    val time =
        runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    val minutes = Duration.between(time, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 14 * 24 * 60 -> "${minutes / (24 * 60)} d ago"
        else -> time.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
}
