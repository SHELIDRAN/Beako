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
import org.nekomanga.desktop.components.Skeleton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.components.formatCount
import org.nekomanga.desktop.components.formatScore
import org.nekomanga.desktop.state.DetailsModel
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.model.Chapter

/** A title's page: cover and details, where it can be read, and its chapters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    model: DetailsModel,
    onBack: () -> Unit,
    /** Opens the reader on [index] of the chapters in reading order, at [page]. */
    onRead: (chapters: List<Chapter>, index: Int, page: Int) -> Unit,
) {
    LaunchedEffect(model) { model.start() }
    val libraryData by model.libraryData.collectAsState()
    val entry = remember(libraryData) { model.entry() }
    val title = model.title
    var expanded by remember { mutableStateOf(false) }
    val chapters = model.chapters
    val readingOrder = remember(chapters) { model.readingOrder() }

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
                            LibraryButton(
                                model,
                                inLibrary = entry != null,
                                categories = entry?.categories.orEmpty(),
                                allCategories = libraryData.categories,
                            )
                            AvailableOn(model)
                        }
                    }
                }
                val count = (chapters as? Loadable.Ready)?.value?.size
                Text(
                    if (count != null && count > 0) "Chapters · $count" else "Chapters",
                    style = MaterialTheme.typography.headlineSmall,
                    color = BeakoColors.Text,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
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
                if (chapters.value.isEmpty()) {
                    item("none") {
                        StateMessage(
                            "No source has this title yet",
                            "It isn’t on MangaDex. Installing source extensions comes in a later version.",
                        )
                    }
                } else {
                    items(chapters.value, key = { it.id }) { chapter ->
                        val read = entry != null && chapter.id in entry.readChapterIds
                        val isNew = entry?.newChapters?.any { it.chapter.id == chapter.id } == true
                        val page = entry?.progress?.takeIf { it.chapterId == chapter.id }?.page
                        ContextMenuArea(
                            items = {
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
                            ChapterRow(chapter, read = read, isNew = isNew, page = page) {
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
    }
}

/** Where the title can be read. A single source for now; it becomes a dropdown with extensions. */
@Composable
private fun AvailableOn(model: DetailsModel) {
    val text =
        when {
            model.chapters is Loadable.Loading -> "Looking for sources…"
            model.availableOn.isEmpty() -> "Not available on any source"
            else -> "Available on ${model.availableOn.joinToString(", ")}"
        }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = BeakoColors.Text,
        modifier =
            Modifier.glass(RoundedCornerShape(50), tint = BeakoColors.Rim)
                .padding(horizontal = 14.dp, vertical = 9.dp),
    )
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
                        chapter.group,
                        chapter.publishedAt.take(10),
                        if (read) "Read" else page?.let { "Page ${it + 1}" },
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
