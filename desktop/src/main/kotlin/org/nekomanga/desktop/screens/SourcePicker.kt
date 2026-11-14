package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.state.DetailsModel
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Sources

/** A source's state for this title, as the picker shows it. */
private enum class Status(val label: String) {
    MATCHED("Matched"),
    NONE("No automatic match"),
    CHECKING("Checking…"),
    ERROR("Error"),
}

/**
 * Where to read this title: MangaDex and every installed source with what automatic matching found,
 * and a search by hand on any of them (romaji, original or alternative names) to pick the right
 * title when matching missed. Comick is never offered: Beako doesn't read from it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcePicker(model: DetailsModel, onClose: () -> Unit) {
    var searchingIn by remember { mutableStateOf<Pair<String, String>?>(null) }
    var query by remember { mutableStateOf(model.title.title) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.width(640.dp)
                .heightIn(max = 680.dp)
                .glass(RoundedCornerShape(22.dp), tint = Color(0xE6101114))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Where to read",
                        style = MaterialTheme.typography.titleLarge,
                        color = BeakoColors.Text,
                    )
                    Text(
                        model.title.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = BeakoColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(Icons.Outlined.Close, "Close", onClose)
            }

            val mangaDexMatched = model.availableOn.any { it.sourceId == Sources.MANGADEX }
            SourceRow(
                name = MangaDexApi.SERVICE,
                status =
                    when {
                        mangaDexMatched -> Status.MATCHED
                        model.mangaDexFailure != null -> Status.ERROR
                        model.mangaDexChecked -> Status.NONE
                        else -> Status.CHECKING
                    },
                detail = model.mangaDexFailure,
                selected = model.selectedSource == Sources.MANGADEX,
                onRead = {
                    model.selectSource(Sources.MANGADEX)
                    onClose()
                },
                onSearch = { searchingIn = Sources.MANGADEX to MangaDexApi.SERVICE },
            )
            model.installedSources.forEach { (id, name) ->
                val check = model.sourceChecks.firstOrNull { it.sourceId == id }
                val matched = model.availableOn.any { it.sourceId == id }
                SourceRow(
                    name = name,
                    status =
                        when {
                            matched -> Status.MATCHED
                            check?.error != null -> Status.ERROR
                            check != null -> Status.NONE
                            model.searchingSources -> Status.CHECKING
                            else -> Status.NONE
                        },
                    detail = check?.error?.takeIf { !matched },
                    selected = model.selectedSource == id,
                    onRead = {
                        model.selectSource(id)
                        onClose()
                    },
                    onSearch = { searchingIn = id to name },
                )
            }
            if (model.installedSources.isEmpty()) {
                Text(
                    "Install sources in More → Sources to read from more sites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }

            searchingIn?.let { (sourceId, sourceName) ->
                HorizontalDivider(color = BeakoColors.Rim)
                Text(
                    "Search $sourceName",
                    style = MaterialTheme.typography.titleMedium,
                    color = BeakoColors.Text,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SearchField(query, { query = it }, "Title on $sourceName", Modifier.weight(1f))
                    GlassButton(
                        "Search",
                        { model.pickerSearch(sourceId, query) },
                        icon = Icons.Outlined.Search,
                        primary = true,
                    )
                }
                val names = (listOf(model.title.title) + model.title.altTitles).distinct().take(5)
                if (names.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        names.forEach { name ->
                            GlassButton(
                                name.take(40),
                                {
                                    query = name
                                    model.pickerSearch(sourceId, name)
                                },
                            )
                        }
                    }
                }
                when (val results = model.pickerResults) {
                    null -> Unit
                    Loadable.Loading ->
                        Text(
                            "Searching…",
                            style = MaterialTheme.typography.bodySmall,
                            color = BeakoColors.TextMuted,
                        )
                    is Loadable.Failed ->
                        Text(
                            "Couldn’t search $sourceName: ${results.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BeakoColors.Danger,
                        )
                    is Loadable.Ready ->
                        if (results.value.isEmpty()) {
                            Text(
                                "No titles found. Try another name.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BeakoColors.TextMuted,
                            )
                        } else {
                            results.value.forEach { result ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        result.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = BeakoColors.Text,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GlassButton(
                                        "Read this",
                                        {
                                            model.bind(sourceId, sourceName, result)
                                            onClose()
                                        },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    status: Status,
    detail: String?,
    selected: Boolean,
    onRead: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = BeakoColors.Text)
            Text(
                if (status == Status.ERROR && detail != null) "Error: $detail" else status.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (status == Status.ERROR) BeakoColors.Danger else BeakoColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            selected ->
                Text(
                    "Reading",
                    style = MaterialTheme.typography.labelLarge,
                    color = BeakoColors.Positive,
                )
            status == Status.MATCHED -> GlassButton("Read here", onRead, primary = true)
        }
        GlassButton("Search", onSearch, icon = Icons.Outlined.Search)
    }
}
