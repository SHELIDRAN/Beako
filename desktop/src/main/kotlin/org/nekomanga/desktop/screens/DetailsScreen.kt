package org.nekomanga.desktop.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.Chip
import org.nekomanga.desktop.components.Cover
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.components.Shelf
import org.nekomanga.desktop.components.Skeleton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.components.formatCount
import org.nekomanga.desktop.components.formatScore
import org.nekomanga.desktop.state.DetailsModel
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.TitleTracking
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.download.DownloadState
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title

/** A title's page: cover and details, where it can be read, and its chapters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    model: DetailsModel,
    onBack: () -> Unit,
    /** Opens the reader on [index] of the chapters in reading order, at [page]. */
    onRead: (chapters: List<Chapter>, index: Int, page: Int) -> Unit,
    tracking: TitleTracking? = null,
    onOpenTitle: (Title) -> Unit = {},
) {
    LaunchedEffect(model) { model.start() }
    val libraryData by model.libraryData.collectAsState()
    val queue by model.downloads.queue.collectAsState()
    val downloadChanges by model.downloads.changes.collectAsState()
    val entry = remember(libraryData) { model.entry() }
    val title = model.title
    var expanded by remember { mutableStateOf(false) }
    val chapters = model.chapters
    val excluded = model.excludedGroups
    val readingOrder = remember(chapters, excluded) { model.readingOrder() }
    val visible = remember(chapters, excluded) { model.visibleChapters() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Cover(
                        title.coverUrl,
                        Modifier.size(width = 220.dp, height = 330.dp)
                            .shadow(18.dp, RoundedCornerShape(18.dp), spotColor = Color.Black)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            title.title,
                            style = MaterialTheme.typography.displaySmall,
                            color = BeakoColors.Text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (title.altTitles.isNotEmpty()) {
                            Text(
                                title.altTitles.take(4).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BeakoColors.TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            title.type?.let { Chip(it.label) }
                            title.status?.let { Chip(it.label) }
                            title.year?.let { Chip(it.toString()) }
                            title.demographic?.let { Chip(it.replaceFirstChar(Char::uppercase)) }
                            title.rating?.let {
                                Chip("★ ${formatScore(it)}", ink = BeakoColors.Gold)
                            }
                            title.follows?.let { Chip("${formatCount(it)} follows") }
                        }
                        if (title.description.isNotBlank()) {
                            Text(
                                title.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = BeakoColors.TextMuted,
                                maxLines = if (expanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier.clickable(
                                        role = Role.Button,
                                        onClickLabel = if (expanded) "Show less" else "Show more",
                                    ) {
                                        expanded = !expanded
                                    },
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (readingOrder.isNotEmpty()) {
                                val resume = remember(entry, readingOrder) { model.continueIndex() }
                                val saved = entry?.progress
                                GlassButton(
                                    if (resume == null) "Start reading"
                                    else "Continue ${readingOrder[resume].displayName()}",
                                    onClick = {
                                        val index = resume ?: 0
                                        val page =
                                            if (
                                                saved != null &&
                                                    readingOrder[index].id == saved.chapterId
                                            )
                                                saved.page
                                            else 0
                                        onRead(readingOrder, index, page)
                                    },
                                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                                    primary = true,
                                )
                            }
                            if (readingOrder.isNotEmpty()) {
                                GlassButton(
                                    "Download unread",
                                    onClick = { model.download(model.unreadChapters()) },
                                    icon = Icons.Outlined.Download,
                                )
                            }
                            LibraryButton(
                                model,
                                inLibrary = entry != null,
                                categories = entry?.categories.orEmpty(),
                                allCategories = libraryData.categories,
                            )
                            AvailableOn(model)
                        }
                        if (tracking != null) TrackingPanel(tracking, entry)
                    }
                }
                val total = (chapters as? Loadable.Ready)?.value?.size ?: 0
                Row(
                    Modifier.padding(top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when {
                            total == 0 -> "Chapters"
                            visible.size < total -> "Chapters · ${visible.size} of $total"
                            else -> "Chapters · $total"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = BeakoColors.Text,
                    )
                    GroupFilter(model, remember(chapters) { model.groups() }, excluded)
                }
            }
        }
        when (chapters) {
            is Loadable.Loading ->
                items(6, key = { "s$it" }) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            is Loadable.Failed ->
                item("failed") {
                    StateMessage("Couldn’t load chapters", chapters.message) {
                        GlassButton("Try again", onClick = model::retry, primary = true)
                    }
                }
            is Loadable.Ready ->
                if (chapters.value.isNotEmpty() && visible.isEmpty()) {
                    item("filtered") {
                        StateMessage(
                            "Every chapter is hidden",
                            "All of this title’s chapters come from groups you’ve hidden. Show a group to see its chapters.",
                        )
                    }
                } else if (chapters.value.isEmpty()) {
                    item("none") {
                        StateMessage(
                            "No source has this title yet",
                            "It isn’t on MangaDex or any source you’ve installed. Add sources in More → Sources.",
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { chapter ->
                        val read = entry != null && chapter.id in entry.readChapterIds
                        val isNew = entry?.newChapters?.any { it.chapter.id == chapter.id } == true
                        val page = entry?.progress?.takeIf { it.chapterId == chapter.id }?.page
                        val job = queue.firstOrNull {
                            it.chapter.id == chapter.id && it.title.id == model.title.id
                        }
                        val downloaded =
                            remember(downloadChanges, chapter.id) { model.isDownloaded(chapter) }
                        val download =
                            when {
                                downloaded -> "Downloaded"
                                job == null -> null
                                job.state == DownloadState.FAILED -> "Download failed"
                                job.total > 0 -> "Downloading ${job.done}/${job.total}"
                                else -> "Queued"
                            }
                        ContextMenuArea(
                            items = {
                                (if (downloaded)
                                    listOf(
                                        ContextMenuItem("Delete download") {
                                            model.deleteDownload(chapter)
                                        },
                                    )
                                else if (chapter.externalUrl == null && job == null)
                                    listOf(
                                        ContextMenuItem("Download") {
                                            model.download(listOf(chapter))
                                        },
                                    )
                                else emptyList()) +
                                    if (entry == null) emptyList()
                                    else
                                        listOf(
                                            if (read)
                                                ContextMenuItem("Mark as unread") {
                                                    model.setRead(listOf(chapter), false)
                                                }
                                            else
                                                ContextMenuItem("Mark as read") {
                                                    model.setRead(listOf(chapter), true)
                                                },
                                            ContextMenuItem("Mark this and earlier as read") {
                                                model.markReadUpTo(chapter)
                                            },
                                        )
                            },
                        ) {
                            ChapterRow(
                                chapter,
                                read = read,
                                isNew = isNew,
                                page = page,
                                download = download,
                                from = model.filledFrom[chapter.id],
                            ) {
                                onRead(
                                    readingOrder,
                                    readingOrder.indexOfFirst { it.id == chapter.id },
                                    page ?: 0,
                                )
                            }
                        }
                    }
                }
        }
        item("similar") {
            val similar = model.similar
            if (similar !is Loadable.Ready || similar.value.isNotEmpty()) {
                Shelf(
                    "More like this",
                    similar,
                    onOpenTitle,
                    Modifier.padding(top = 28.dp),
                    inset = 0.dp,
                )
            }
        }
    }
}

/** Shows or hides chapters by scanlation group. Hidden only when there's a single group. */
@Composable
private fun GroupFilter(model: DetailsModel, groups: List<String>, excluded: Set<String>) {
    if (groups.size < 2 && excluded.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        GlassButton(
            if (excluded.isEmpty()) "All groups" else "${excluded.size} hidden",
            onClick = { open = true },
            icon = Icons.Outlined.FilterList,
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "Show chapters from",
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            (groups + excluded.filter { it !in groups }).forEach { group ->
                val shown = group !in excluded
                DropdownMenuItem(
                    text = { Text((if (shown) "✓  " else "     ") + group) },
                    onClick = { model.setGroupHidden(group, hidden = shown) },
                )
            }
        }
    }
}

/**
 * Where the title can be read: MangaDex and the installed sources that carry it. Picking one loads
 * its chapters; Comick is never listed (Beako doesn't read from it).
 */
@Composable
private fun AvailableOn(model: DetailsModel) {
    var open by remember { mutableStateOf(false) }
    val selected = model.availableOn.firstOrNull { it.sourceId == model.selectedSource }
    val text =
        when {
            selected != null -> "Reading from ${selected.name}"
            model.chapters is Loadable.Loading || model.searchingSources -> "Looking for sources…"
            else -> "Not available on any source"
        }
    Box {
        GlassButton(
            text + if (model.searchingSources && selected != null) " · searching…" else "",
            onClick = { if (model.availableOn.size > 1 || model.searchingSources) open = true },
            icon = if (model.availableOn.size > 1) Icons.Outlined.ExpandMore else null,
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "Available on",
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            model.availableOn.forEach { match ->
                val on = match.sourceId == model.selectedSource
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (on) "✓  " else "     ") +
                                match.name +
                                if (match.unoriginal) " (copy of another site)" else "",
                        )
                    },
                    onClick = {
                        model.selectSource(match.sourceId)
                        open = false
                    },
                )
            }
            if (model.searchingSources) {
                Text(
                    "Still searching your installed sources…",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Add to library, or, when it's there, a menu to change its categories or remove it. */
@Composable
private fun LibraryButton(
    model: DetailsModel,
    inLibrary: Boolean,
    categories: List<String>,
    allCategories: List<String>,
) {
    var open by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    Box {
        GlassButton(
            if (inLibrary) "In library" else "Add to library",
            onClick = {
                if (inLibrary) open = true
                else {
                    model.addToLibrary()
                    open = true
                }
            },
            icon = if (inLibrary) Icons.Outlined.LibraryAddCheck else Icons.Outlined.LibraryAdd,
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "Categories (optional)",
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            allCategories.forEach { name ->
                val on = name in categories
                DropdownMenuItem(
                    text = { Text((if (on) "✓  " else "     ") + name) },
                    onClick = {
                        model.setCategories(if (on) categories - name else categories + name)
                    },
                )
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchField(
                    newCategory,
                    { newCategory = it },
                    "New category",
                    Modifier.width(200.dp),
                )
                GlassButton(
                    "Add",
                    onClick = {
                        val name = newCategory.trim()
                        if (name.isNotEmpty()) {
                            model.addCategory(name)
                            model.setCategories(categories + name)
                            newCategory = ""
                        }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from library", color = BeakoColors.Danger) },
                onClick = {
                    model.removeFromLibrary()
                    open = false
                },
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    read: Boolean,
    isNew: Boolean,
    page: Int?,
    download: String?,
    /** The source a chapter was filled in from, when it isn't the chosen one. */
    from: String? = null,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val name = chapter.displayName()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) Color(0x12FFFFFF) else Color.Transparent)
            .hoverable(hover)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isNew) Chip("New")
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (read) BeakoColors.TextMuted else BeakoColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(
                        from?.let { "from $it" },
                        chapter.group,
                        chapter.publishedAt.take(10),
                        if (read) "Read" else page?.let { "Page ${it + 1}" },
                        download,
                    )
                    .joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
        if (chapter.externalUrl != null) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                "Opens in your browser",
                tint = BeakoColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                "${chapter.pages} pages",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
    }
}
