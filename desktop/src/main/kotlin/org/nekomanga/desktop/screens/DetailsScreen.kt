package org.nekomanga.desktop.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.nekomanga.desktop.components.ArrowButton
import org.nekomanga.desktop.components.ArrowStyle
import org.nekomanga.desktop.components.BeakoDropdownMenu
import org.nekomanga.desktop.components.BeakoMenuItem
import org.nekomanga.desktop.components.Chip
import org.nekomanga.desktop.components.Cover
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.components.Shelf
import org.nekomanga.desktop.components.Skeleton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.components.ToggleIconButton
import org.nekomanga.desktop.components.formatCount
import org.nekomanga.desktop.components.formatScore
import org.nekomanga.desktop.components.hoverSweep
import org.nekomanga.desktop.state.DetailsModel
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.TitleTracking
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.state.pageModel
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.CoverColour
import org.nekomanga.desktop.theme.focusRing
import org.nekomanga.shared.download.DownloadState
import org.nekomanga.shared.library.Settings
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
    settings: Settings = Settings(),
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
    // The cover of the volume being read, with "Dynamic covers".
    val cover = remember(chapters, entry, model.volumeCovers) { model.coverUrl() }
    // "Theme buttons based on cover": the page's accents take the cover's colour.
    LaunchedEffect(cover, settings.themeFromCover) {
        BeakoColors.coverAccent =
            if (settings.themeFromCover && cover != null)
                runCatching { CoverColour.of(pageModel(cover), cover) }.getOrNull()
            else null
    }
    DisposableEffect(Unit) { onDispose { BeakoColors.coverAccent = null } }
    val isLocal = title.source == org.nekomanga.shared.model.Sources.LOCAL

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item("header") {
            Box {
                Backdrop(cover, settings.backdrop)
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    GlassIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        "Back",
                        onBack,
                        swingFrom = -90f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Cover(
                            cover,
                            Modifier.size(width = 220.dp, height = 330.dp)
                                .shadow(18.dp, RoundedCornerShape(18.dp), spotColor = Color.Black)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                title.title,
                                style = MaterialTheme.typography.displaySmall,
                                color = BeakoColors.Text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (title.altTitles.isNotEmpty()) {
                                AltTitles(title.altTitles, wrap = settings.wrapAltTitles)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                title.type?.let { Chip(it.label) }
                                title.status?.let { Chip(it.label) }
                                title.year?.let { Chip(it.toString()) }
                                title.demographic?.let {
                                    Chip(it.replaceFirstChar(Char::uppercase))
                                }
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
                                            onClickLabel =
                                                if (expanded) "Show less" else "Show more",
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
                                    val resume =
                                        remember(entry, readingOrder) { model.continueIndex() }
                                    val saved = entry?.progress
                                    ArrowButton(
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
                                        style = ArrowStyle.ACCENT,
                                    )
                                }
                                // Local manga is already on this PC.
                                if (readingOrder.isNotEmpty() && !isLocal) {
                                    GlassButton(
                                        "Download unread",
                                        onClick = { model.download(model.unreadChapters()) },
                                        icon = Icons.Outlined.Download,
                                        iconOnly = settings.hideButtonText,
                                    )
                                }
                                LibraryButton(
                                    model,
                                    inLibrary = entry != null,
                                    categories = entry?.categories.orEmpty(),
                                    allCategories = libraryData.categories,
                                )
                                AvailableOn(model, iconOnly = settings.hideButtonText)
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
            // Candidates ranked by the user's weights (More → Library → More like this).
            val similar =
                when (val pool = model.similar) {
                    is Loadable.Ready ->
                        Loadable.Ready(
                            org.nekomanga.shared.discovery.Similar.rank(
                                model.title,
                                pool.value,
                                settings.similarWeights,
                            ),
                        )
                    else -> pool
                }
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
        BeakoDropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "Show chapters from",
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            (groups + excluded.filter { it !in groups }).forEach { group ->
                val shown = group !in excluded
                BeakoMenuItem(
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
private fun AvailableOn(model: DetailsModel, iconOnly: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val selected = model.availableOn.firstOrNull { it.sourceId == model.selectedSource }
    val text =
        when {
            selected != null -> "Reading from ${selected.name}"
            model.chapters is Loadable.Loading || model.searchingSources -> "Looking for sources…"
            else -> "Not available on any source"
        }
    val failures = model.sourceChecks.count { it.error != null }
    // Always opens the picker: pick another source, or search one by hand when matching missed.
    GlassButton(
        text + (if (failures > 0) " · $failures failed" else ""),
        onClick = { open = true },
        icon = Icons.Outlined.ExpandMore,
        iconOnly = iconOnly,
    )
    if (open) SourcePicker(model, onClose = { open = false })
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
        // + adds the title and offers categories; − opens the same menu, where removing is one
        // deliberate step, so a stray click never drops a series and its progress.
        ToggleIconButton(
            checked = inLibrary,
            onClick = {
                // "Default category" decides whether to ask.
                open = if (!inLibrary) model.addWithDefaultCategory() else true
            },
            offLabel = "Add to library",
            onLabel = "In library: change categories or remove",
            ghost = true,
            role = Role.Button,
        )
        BeakoDropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "Categories (optional)",
                style = MaterialTheme.typography.labelMedium,
                color = BeakoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            allCategories.forEach { name ->
                val on = name in categories
                BeakoMenuItem(
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
            BeakoMenuItem(
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
    val name = chapter.displayName()
    Row(
        Modifier.fillMaxWidth()
            .focusRing(hover, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .hoverSweep(hover)
            .hoverable(hover)
            .clickable(
                interactionSource = hover,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
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

/** The cover, blurred and faded, behind the top of the title page ("backdrop size"). */
@Composable
private fun Backdrop(cover: String?, size: String) {
    val height =
        when (size) {
            "OFF" -> return
            "SMALL" -> 260.dp
            "LARGE" -> 520.dp
            else -> 380.dp
        }
    if (cover == null) return
    val ground = BeakoColors.Ground
    Box(
        Modifier.fillMaxWidth()
            .height(height)
            // Out to the window's edges, past the page's 32 dp side margins.
            .layout { measurable, constraints ->
                val extra = 64.dp.roundToPx()
                val width = constraints.maxWidth + extra
                val placeable =
                    measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                layout(constraints.maxWidth, placeable.height) {
                    placeable.place(-extra / 2, -28.dp.roundToPx())
                }
            },
    ) {
        AsyncImage(
            model = pageModel(cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize()
                    // Fades out toward the bottom so it melts into whatever is behind the page.
                    .graphicsLayer(
                        compositingStrategy =
                            androidx.compose.ui.graphics.CompositingStrategy.Offscreen,
                    )
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                0.55f to Color.Black.copy(alpha = 0.6f),
                                1f to Color.Transparent,
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                    .blur(48.dp)
                    .alpha(0.5f),
        )
        // A light wash of the ground keeps text over the backdrop readable.
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ground.copy(alpha = 0.35f),
                            ground.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** Alternative names: wrapped in full, or one line you can scroll sideways. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AltTitles(names: List<String>, wrap: Boolean) {
    val chip: @Composable (String) -> Unit = { name ->
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = BeakoColors.TextMuted,
            maxLines = 1,
            modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(BeakoColors.ChipFill)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
    if (wrap) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            names.forEach { chip(it) }
        }
    } else {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            names.forEach { chip(it) }
        }
    }
}
