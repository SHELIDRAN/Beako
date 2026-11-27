package org.nekomanga.desktop.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.BeakoDropdownMenu
import org.nekomanga.desktop.components.BeakoMenuItem
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.MorphSelect
import org.nekomanga.desktop.components.SoftField
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.components.TitleCard
import org.nekomanga.desktop.state.GroupBy
import org.nekomanga.desktop.state.LibraryGroup
import org.nekomanga.desktop.state.LibraryModel
import org.nekomanga.desktop.state.LibrarySort
import org.nekomanga.desktop.state.rememberSearchShortcut
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.model.Title

/** Lower case, letters and digits only, for matching search text against titles. */
fun normaliseForSearch(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/** The library: search, category filter, group by and sort over a grid of saved titles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    model: LibraryModel,
    onOpen: (Title) -> Unit,
    /** Searches the sources for text that matched nothing in the library. */
    onSearchSources: (String) -> Unit,
    onBrowse: () -> Unit,
    /** Changes whenever a download finishes or is deleted. */
    downloadChanges: Int = 0,
) {
    val data by model.store.data.collectAsState()
    val groups =
        remember(
            data,
            model.query,
            model.categoryFilter,
            model.groupBy,
            model.sort,
            model.descending,
            model.downloadedOnly,
            downloadChanges,
        ) {
            model.groups(data.entries)
        }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(172.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Library",
                    style = MaterialTheme.typography.displaySmall,
                    color = BeakoColors.Text,
                )
                // The library sits on the plain ground, so its search is the soft field.
                SoftField(
                    model.query,
                    { model.query = it },
                    "Search library by name, tag or genre",
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shortcut = "Ctrl F",
                    focusRequester = rememberSearchShortcut(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategoryMenu(model, data.categories)
                    if (groups.count { it.name.isNotEmpty() } > 1) {
                        val all = model.allCollapsed(groups)
                        GlassButton(
                            if (all) "Expand all" else "Collapse all",
                            onClick = { model.toggleAll(groups) },
                            icon =
                                if (all) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                        )
                    }
                    Menu(
                        "Group by: ${model.groupBy.label}",
                        GroupBy.entries.map { it.label },
                    ) {
                        model.groupBy = GroupBy.entries[it]
                    }
                    Menu("Sort: ${model.sort.label}", LibrarySort.entries.map { it.label }) {
                        model.sort = LibrarySort.entries[it]
                    }
                    MorphSelect(
                        listOf("Ascending", "Descending"),
                        if (model.descending) 1 else 0,
                        { model.descending = it == 1 },
                        label = "Sort order",
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        when {
            data.entries.isEmpty() ->
                item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage(
                        "Your library is empty",
                        "Find a title in Browse and choose “Add to library”.",
                    ) {
                        GlassButton(
                            "Browse",
                            onClick = onBrowse,
                            icon = Icons.Outlined.Explore,
                            primary = true,
                        )
                    }
                }
            groups.isEmpty() ->
                item("nomatch", span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage("Nothing in your library matches") {
                        if (model.query.isNotBlank()) {
                            GlassButton(
                                "Search sources for “${model.query.trim()}”",
                                onClick = { onSearchSources(model.query.trim()) },
                                icon = Icons.Outlined.Search,
                                primary = true,
                            )
                        }
                    }
                }
            else ->
                groups.forEach { group ->
                    val folded = group.name.isNotEmpty() && model.isCollapsed(group)
                    if (group.name.isNotEmpty()) {
                        item("g:${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                            GroupHeader(group, folded) { model.toggle(group) }
                        }
                    }
                    if (!folded)
                        items(group.entries, key = { "${group.name}|${it.key}" }) { entry ->
                            Box(Modifier.width(172.dp)) {
                                TitleCard(
                                    entry.title,
                                    onClick = { onOpen(entry.title) },
                                    badge = entry.unreadCount.takeIf { it > 0 }?.toString(),
                                )
                            }
                        }
                }
        }
    }
}

@Composable
private fun CategoryMenu(model: LibraryModel, categories: List<String>) {
    if (categories.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val label =
        when (model.categoryFilter.size) {
            0 -> "All categories"
            1 -> model.categoryFilter.first()
            else -> "${model.categoryFilter.size} categories"
        }
    Box {
        GlassButton(label, onClick = { open = true })
        BeakoDropdownMenu(open, onDismissRequest = { open = false }) {
            BeakoMenuItem(
                text = { Text("All categories") },
                onClick = {
                    model.categoryFilter = emptySet()
                    open = false
                },
            )
            categories.forEach { name ->
                val on = name in model.categoryFilter
                BeakoMenuItem(
                    text = { Text((if (on) "✓  " else "     ") + name) },
                    onClick = {
                        model.categoryFilter =
                            if (on) model.categoryFilter - name else model.categoryFilter + name
                    },
                )
            }
        }
    }
}

@Composable
internal fun Menu(label: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassButton(label, onClick = { open = true })
        BeakoDropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                BeakoMenuItem(
                    text = { Text(option) },
                    onClick = {
                        open = false
                        onPick(index)
                    },
                )
            }
        }
    }
}

/**
 * A group's heading: its name and count with a chevron. Clicking it folds the group's titles away
 * or brings them back; the choice is remembered.
 */
@Composable
private fun GroupHeader(group: LibraryGroup, folded: Boolean, onToggle: () -> Unit) {
    val turn by
        androidx.compose.animation.core.animateFloatAsState(
            if (folded) -90f else 0f,
            org.nekomanga.desktop.theme.sweepSpec(),
            label = "chevron",
        )
    Row(
        Modifier.padding(top = 8.dp)
            .clip(
                androidx.compose.foundation.shape.RoundedCornerShape(
                    org.nekomanga.desktop.theme.Radius.field,
                ),
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (folded) "Collapsed" else "Expanded" }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = BeakoColors.TextMuted,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
        )
        Text(
            "${group.name} · ${group.entries.size}",
            style = MaterialTheme.typography.titleLarge,
            color = BeakoColors.Text,
        )
    }
}

/** A row of items with its label, used by the screens' toolbars. */
@Composable
internal fun ToolbarRow(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}
