package org.nekomanga.desktop.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.Banner
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.ResultRow
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.components.Skeleton
import org.nekomanga.desktop.components.StateMessage
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.SearchModel
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.comick.ComickFilters
import org.nekomanga.shared.comick.ComickSort
import org.nekomanga.shared.comick.Demographic
import org.nekomanga.shared.discovery.FallbackReason
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

private val MoreSorts =
    listOf(
        ComickSort.BAYESIAN_RATING to ("Bayesian rating" to "More votes, more trust."),
        ComickSort.AVERAGE_RATING to ("Average rating" to "Simple average of ratings."),
        ComickSort.LAST_UPDATED to ("Last updated" to "Newest chapter first."),
    )

/** Search: keyword, sort, the filter panel and ranked results with infinite scroll. */
@Composable
fun SearchScreen(
    model: SearchModel,
    initialQuery: String,
    onBack: () -> Unit,
    onOpen: (Title) -> Unit,
) {
    LaunchedEffect(model) { model.start(initialQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Open the filters when browsing; keep them out of the way of keyword results.
    var showFilters by remember { mutableStateOf(initialQuery.isBlank()) }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, model.titles.size) { if (nearEnd) model.loadMore() }
    val filters = model.filters
    val onMangaDex = model.servedBy == Sources.MANGADEX && model.status is Loadable.Ready

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                    Text(
                        "Search",
                        style = MaterialTheme.typography.displaySmall,
                        color = BeakoColors.Text,
                    )
                }
                SearchField(
                    filters.query,
                    { text -> model.update { it.copy(query = text) } },
                    "Search titles",
                    Modifier.fillMaxWidth(),
                    focus,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SortControl(filters.sort) { sort -> model.update { it.copy(sort = sort) } }
                    GlassButton(
                        when {
                            filters.activeCount > 0 -> "Filters · ${filters.activeCount}"
                            showFilters -> "Hide filters"
                            else -> "Filters"
                        },
                        onClick = { showFilters = !showFilters },
                        icon = Icons.Outlined.Tune,
                    )
                }
                AnimatedVisibility(showFilters && !onMangaDex) { FilterPanel(model) }
                when {
                    model.fallback == FallbackReason.COMICK_UNAVAILABLE && onMangaDex ->
                        Banner("Showing MangaDex results — Comick is unavailable.") {
                            GlassButton("Try Comick again", onClick = model::retryComick)
                        }
                    model.fallback == FallbackReason.COMICK_FOUND_NOTHING && onMangaDex ->
                        Banner("Showing MangaDex results — Comick found no matches.") {
                            GlassButton("Try Comick again", onClick = model::retryComick)
                        }
                    onMangaDex ->
                        Banner("Showing MangaDex results.") {
                            GlassButton("Back to Comick", onClick = model::retryComick)
                        }
                }
            }
        }
        when (val status = model.status) {
            is Loadable.Loading ->
                items(6, key = { "skeleton$it" }) {
                    Skeleton(Modifier.fillMaxWidth().height(146.dp), 16)
                }
            is Loadable.Failed ->
                item("failed") {
                    StateMessage("Couldn’t load results", status.message) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton("Try again", onClick = model::retryComick, primary = true)
                            if (filters.hasKeyword) {
                                GlassButton("Search MangaDex", onClick = model::searchMangaDex)
                            }
                        }
                    }
                }
            is Loadable.Ready -> {
                if (model.titles.isEmpty()) {
                    item("empty") {
                        if (filters.activeCount > 0) {
                            StateMessage("No comics match these filters") {
                                GlassButton("Clear all filters", { model.update { it.cleared() } })
                            }
                        } else {
                            StateMessage("No results", "Try a different title or spelling.")
                        }
                    }
                }
                itemsIndexed(model.titles, key = { _, t -> "${t.source}:${t.id}" }) { index, title
                    ->
                    ResultRow(index + 1, title, onClick = { onOpen(title) })
                }
                if (model.loadingMore) {
                    item("more") { Skeleton(Modifier.fillMaxWidth().height(146.dp), 16) }
                } else if (!model.hasMore && model.titles.isNotEmpty()) {
                    item("end") {
                        Text(
                            "That’s everything — refine the search for more.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BeakoColors.TextMuted,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortControl(sort: ComickSort, onSort: (ComickSort) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropletSegmented(
            options = listOf("Latest", "Popular"),
            selectedIndex =
                when (sort) {
                    ComickSort.LATEST -> 0
                    ComickSort.POPULAR -> 1
                    else -> -1
                },
            onSelect = { onSort(if (it == 0) ComickSort.LATEST else ComickSort.POPULAR) },
            label = "Sort",
            optionWidth = 76.dp,
        )
        Box {
            val current = MoreSorts.firstOrNull { it.first == sort }?.second?.first
            GlassButton(current ?: "More", onClick = { menu = true })
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                MoreSorts.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(text.first, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BeakoColors.TextMuted,
                                )
                            }
                        },
                        onClick = {
                            menu = false
                            onSort(value)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(model: SearchModel) {
    val filters = model.filters
    Column(
        Modifier.fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), tint = BeakoColors.Rim)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TagPicker(model)
        GenrePicker(model)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FilterGroup("Type") {
                ComicType.entries.forEach { type ->
                    ToggleChip(
                        if (type == ComicType.OTHER) "Others" else type.label,
                        type in filters.types,
                    ) {
                        model.update { it.copy(types = it.types.toggle(type)) }
                    }
                }
            }
            FilterGroup("Demographic") {
                Demographic.entries.forEach { demographic ->
                    ToggleChip(demographic.label, demographic in filters.demographics) {
                        model.update { it.copy(demographics = it.demographics.toggle(demographic)) }
                    }
                }
            }
            FilterGroup("Status") {
                listOf(1 to "Ongoing", 2 to "Completed", 3 to "Cancelled", 4 to "Hiatus").forEach {
                    (code, name) ->
                    ToggleChip(name, filters.status == code) {
                        model.update { it.copy(status = if (it.status == code) null else code) }
                    }
                }
                ToggleChip("Translation completed", filters.translationCompleted) {
                    model.update { it.copy(translationCompleted = !it.translationCompleted) }
                }
            }
            FilterGroup("Added to Comick") {
                listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year").forEach {
                    (days, name) ->
                    ToggleChip(name, filters.addedWithinDays == days) {
                        model.update {
                            it.copy(
                                addedWithinDays = if (it.addedWithinDays == days) null else days,
                            )
                        }
                    }
                }
            }
            FilterGroup("Min score") {
                listOf(6.0, 7.0, 8.0, 9.0).forEach { score ->
                    ToggleChip("≥ ${score.toInt()}", filters.minRating == score) {
                        model.update {
                            it.copy(minRating = if (it.minRating == score) null else score)
                        }
                    }
                }
            }
            FilterGroup("More") {
                ToggleChip("Include comics without chapters", filters.includeWithoutChapters) {
                    model.update { it.copy(includeWithoutChapters = !it.includeWithoutChapters) }
                }
            }
        }
        if (filters.activeCount > 0) {
            GlassButton("Clear all filters", { model.update { it.cleared() } })
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(name: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall, color = BeakoColors.Text)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** One entry of a tri-state picker. */
private data class Choice(val slug: String, val name: String, val count: Int?)

@Composable
private fun TagPicker(model: SearchModel) {
    val filters = model.filters
    TriStatePicker(
        name = "Tags",
        placeholder = "Search tags",
        choices = model.tags.map { tags -> tags.map { Choice(it.slug, it.name, it.count) } },
        included = filters.includedTags,
        excluded = filters.excludedTags,
        maxIncluded = ComickFilters.MAX_INCLUDED,
        maxExcluded = ComickFilters.MAX_EXCLUDED_TAGS,
        useOf = filters::tagUse,
        onCycle = { slug -> model.update { it.cycleTag(slug) } },
    )
}

@Composable
private fun GenrePicker(model: SearchModel) {
    val filters = model.filters
    TriStatePicker(
        name = "Genres",
        placeholder = "Search genres",
        choices =
            model.genres.map { genres ->
                genres
                    .sortedByDescending { it.count ?: 0 }
                    .map { Choice(it.slug, it.name, it.count) }
            },
        included = filters.includedGenres,
        excluded = filters.excludedGenres,
        maxIncluded = ComickFilters.MAX_INCLUDED,
        maxExcluded = ComickFilters.MAX_EXCLUDED_GENRES,
        useOf = filters::genreUse,
        onCycle = { slug -> model.update { it.cycleGenre(slug) } },
    )
}

private fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> =
    when (this) {
        is Loadable.Ready -> Loadable.Ready(transform(value))
        is Loadable.Loading -> Loadable.Loading
        is Loadable.Failed -> this
    }

/**
 * A searchable list of tri-state chips. Chosen entries stay visible; the rest shows the most-used
 * matches for the search text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriStatePicker(
    name: String,
    placeholder: String,
    choices: Loadable<List<Choice>>,
    included: List<String>,
    excluded: List<String>,
    maxIncluded: Int,
    maxExcluded: Int,
    useOf: (String) -> ComickFilters.Use,
    onCycle: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = BeakoColors.Text)
            Text(
                "Click to include, again to exclude · ${included.size}/$maxIncluded included, " +
                    "${excluded.size}/$maxExcluded excluded",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
        }
        when (choices) {
            is Loadable.Loading -> Skeleton(Modifier.fillMaxWidth().height(40.dp))
            is Loadable.Failed ->
                Text(
                    "$name couldn’t be loaded: ${choices.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            is Loadable.Ready -> {
                SearchField(query, { query = it }, placeholder, Modifier.width(360.dp))
                val chosen = (included + excluded).toSet()
                val shown =
                    remember(query, choices.value, chosen) {
                        val selected = choices.value.filter { it.slug in chosen }
                        val matches =
                            choices.value
                                .asSequence()
                                .filter { it.slug !in chosen }
                                .filter {
                                    query.isBlank() ||
                                        it.name.contains(query.trim(), ignoreCase = true)
                                }
                                .take(14)
                                .toList()
                        selected + matches
                    }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    shown.forEach { choice ->
                        val label =
                            if (choice.count != null) "${choice.name} (${choice.count})"
                            else choice.name
                        TriStateChip(label, useOf(choice.slug)) { onCycle(choice.slug) }
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        "No ${name.lowercase()} match “${query.trim()}”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BeakoColors.TextMuted,
                    )
                }
            }
        }
    }
}

/** A chip that is on or off. */
@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TriStateChip(
        label,
        if (selected) ComickFilters.Use.INCLUDED else ComickFilters.Use.NONE,
        onClick,
    )
}

/**
 * A chip with three states: none, included (✓) and excluded (✕ with a strikethrough), so state is
 * never shown by colour alone.
 */
@Composable
private fun TriStateChip(label: String, use: ComickFilters.Use, onClick: () -> Unit) {
    val shape = CircleShape
    val (fill, rim) =
        when (use) {
            ComickFilters.Use.NONE -> Color(0x0FFFFFFF) to BeakoColors.Rim
            ComickFilters.Use.INCLUDED -> Color(0x33FFFFFF) to Color(0x80FFFFFF)
            ComickFilters.Use.EXCLUDED -> Color(0x14FF8A80) to BeakoColors.Danger.copy(alpha = 0.6f)
        }
    val state =
        when (use) {
            ComickFilters.Use.NONE -> "Not selected"
            ComickFilters.Use.INCLUDED -> "Included"
            ComickFilters.Use.EXCLUDED -> "Excluded"
        }
    Row(
        Modifier.height(32.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = state
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (use) {
            ComickFilters.Use.INCLUDED ->
                Text("✓", style = MaterialTheme.typography.labelMedium, color = BeakoColors.Text)
            ComickFilters.Use.EXCLUDED ->
                Text("✕", style = MaterialTheme.typography.labelMedium, color = BeakoColors.Danger)
            ComickFilters.Use.NONE -> Unit
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (use == ComickFilters.Use.NONE) BeakoColors.TextMuted else BeakoColors.Text,
            textDecoration =
                if (use == ComickFilters.Use.EXCLUDED) TextDecoration.LineThrough else null,
        )
    }
}
