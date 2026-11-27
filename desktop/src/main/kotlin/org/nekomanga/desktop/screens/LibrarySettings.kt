package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.unit.dp
import java.io.File
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.GlassTextField
import org.nekomanga.desktop.components.TriState
import org.nekomanga.desktop.components.TriStateChip
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.SettingsStore

/**
 * More → Library: the title page, library behaviour, categories and the local manga folder.
 * [localFolder] is the folder in use; [defaultLocalFolder] the one used when none is set.
 */
@Composable
internal fun LibrarySettings(
    settingsStore: SettingsStore,
    library: LibraryStore,
    localFolder: File,
    defaultLocalFolder: File,
) {
    val settings by settingsStore.settings.collectAsState()
    val data by library.data.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Section("Title page") {
            ToggleRow(
                "Theme buttons based on cover",
                "Tint the title page’s buttons and highlights with the colour of the cover.",
                settings.themeFromCover,
            ) {
                settingsStore.update { s -> s.copy(themeFromCover = it) }
            }
            ToggleRow(
                "Dynamic covers",
                "Show the cover of the volume you’re reading (titles on MangaDex).",
                settings.dynamicCovers,
            ) {
                settingsStore.update { s -> s.copy(dynamicCovers = it) }
            }
            ChoiceRow(
                "Backdrop",
                listOf(
                    "OFF" to "Off",
                    "SMALL" to "Small",
                    "DEFAULT" to "Default",
                    "LARGE" to "Large",
                ),
                settings.backdrop,
                optionWidth = 72,
            ) {
                settingsStore.update { s -> s.copy(backdrop = it) }
            }
            ToggleRow(
                "Hide button text",
                "Show the title page’s buttons as icons; their names appear on hover.",
                settings.hideButtonText,
            ) {
                settingsStore.update { s -> s.copy(hideButtonText = it) }
            }
            ToggleRow(
                "Wrap alternative titles",
                "Show every alternative title. Off, they sit on one line you can scroll.",
                settings.wrapAltTitles,
            ) {
                settingsStore.update { s -> s.copy(wrapAltTitles = it) }
            }
        }

        Section("More like this") {
            Text(
                "How the title page picks similar titles: each one shares some of the title's genres and " +
                    "themes, then these weights put them in order. Off ignores a quality.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
            val w = settings.similarWeights
            fun set(
                change:
                    (
                        org.nekomanga.shared.discovery.SimilarWeights,
                    ) -> org.nekomanga.shared.discovery.SimilarWeights,
            ) = settingsStore.update { it.copy(similarWeights = change(it.similarWeights)) }
            fun shown(v: Float) = if (v <= 0f) "Off" else "${(v * 100).toInt()}%"
            SliderRow("Shared genres", shown(w.genres), w.genres, 0f..1f) { v ->
                set { it.copy(genres = v) }
            }
            SliderRow("Popularity", shown(w.popularity), w.popularity, 0f..1f) { v ->
                set { it.copy(popularity = v) }
            }
            SliderRow("Rating", shown(w.rating), w.rating, 0f..1f) { v ->
                set { it.copy(rating = v) }
            }
            SliderRow("Same type", shown(w.sameType), w.sameType, 0f..1f) { v ->
                set { it.copy(sameType = v) }
            }
            SliderRow("Newer titles", shown(w.newer), w.newer, 0f..1f) { v ->
                set { it.copy(newer = v) }
            }
            if (w != org.nekomanga.shared.discovery.SimilarWeights()) {
                GlassButton(
                    "Reset to defaults",
                    { set { org.nekomanga.shared.discovery.SimilarWeights() } },
                )
            }
        }
        Section("Library") {
            ToggleRow(
                "Sort by ignoring articles",
                "Sort titles alphabetically without a leading “a”, “an” or “the”.",
                settings.sortIgnoreArticles,
            ) {
                settingsStore.update { s -> s.copy(sortIgnoreArticles = it) }
            }
            ChoiceRow(
                "Hidden groups",
                listOf("ANY" to "Any match", "ALL" to "All match"),
                settings.groupFilter,
                optionWidth = 104,
            ) {
                settingsStore.update { s -> s.copy(groupFilter = it) }
            }
            Text(
                "When a chapter is by several groups: “Any match” hides it if any of them is hidden, " +
                    "“All match” only when all of them are.",
                style = MaterialTheme.typography.bodySmall,
                color = BeakoColors.TextMuted,
            )
            val defaults =
                listOf(null to "Always ask", "" to "None") + data.categories.map { it to it }
            LabeledMenu(
                "Default category",
                defaults.firstOrNull { it.first == settings.defaultCategory }?.second
                    ?: "Always ask",
                defaults.map { it.second },
            ) { index ->
                settingsStore.update { it.copy(defaultCategory = defaults[index].first) }
            }
        }

        Section("Categories") { CategoryEditor(library, data.categories) }

        Section("Local manga") {
            ToggleRow(
                "Read manga from this PC",
                "Each folder in the local manga folder is a series; inside it, each folder of images or " +
                    ".cbz/.zip file is a chapter. A cover.jpg in the series folder is its cover.",
                settings.localManga,
            ) {
                settingsStore.update { s -> s.copy(localManga = it) }
            }
            if (settings.localManga) {
                Text(
                    localFolder.absolutePath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        "Open folder",
                        onClick = {
                            localFolder.mkdirs()
                            openFolder(localFolder)
                        },
                        icon = Icons.Outlined.FolderOpen,
                    )
                    GlassButton(
                        "Change…",
                        onClick = {
                            chooseFolder(localFolder)?.let { chosen ->
                                settingsStore.update { it.copy(localFolder = chosen.absolutePath) }
                            }
                        },
                    )
                    if (settings.localFolder.isNotBlank()) {
                        GlassButton(
                            "Use the default",
                            onClick = { settingsStore.update { it.copy(localFolder = "") } },
                        )
                    }
                }
                Text(
                    "Your series show on Browse under “On this PC”. The default folder is " +
                        defaultLocalFolder.absolutePath +
                        ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }
        }
    }
}

/** Rename, reorder, delete and add library categories. */
@Composable
private fun CategoryEditor(library: LibraryStore, categories: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (categories.isEmpty()) {
            Text(
                "No categories yet. Add one below, or from a title’s library button.",
                style = MaterialTheme.typography.bodyMedium,
                color = BeakoColors.TextMuted,
            )
        }
        categories.forEachIndexed { index, name ->
            var renaming by remember(name) { mutableStateOf(false) }
            var draft by remember(name) { mutableStateOf(name) }
            var confirmDelete by remember(name) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (renaming) {
                    GlassTextField(
                        "New name for $name",
                        draft,
                        { draft = it },
                        Modifier.width(260.dp),
                    )
                    GlassButton(
                        "Save",
                        onClick = {
                            library.renameCategory(name, draft)
                            renaming = false
                        },
                        primary = true,
                    )
                    GlassIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        "Cancel renaming",
                        { renaming = false },
                    )
                } else {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = BeakoColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (index > 0) {
                        GlassIconButton(
                            Icons.Outlined.ArrowUpward,
                            "Move $name up",
                            { library.moveCategory(name, -1) },
                        )
                    }
                    if (index < categories.lastIndex) {
                        GlassIconButton(
                            Icons.Outlined.ArrowDownward,
                            "Move $name down",
                            { library.moveCategory(name, 1) },
                        )
                    }
                    GlassIconButton(Icons.Outlined.Edit, "Rename $name", { renaming = true })
                    if (confirmDelete) {
                        // Deleting takes the category off every title: one more click to be sure.
                        GlassButton("Delete $name", onClick = { library.deleteCategory(name) })
                        GlassButton("Keep", onClick = { confirmDelete = false })
                    } else {
                        GlassIconButton(
                            Icons.Outlined.DeleteOutline,
                            "Delete $name",
                            { confirmDelete = true },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = BeakoColors.Rim)
        var newName by remember { mutableStateOf("") }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassTextField("New category", newName, { newName = it }, Modifier.width(260.dp))
            GlassButton(
                "Add",
                onClick = {
                    library.addCategory(newName)
                    newName = ""
                },
            )
        }
    }
}

/** A setting with a few short choices, as a droplet switch. */
@Composable
internal fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    optionWidth: Int = 88,
    onSelect: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = BeakoColors.Text,
            modifier = Modifier.width(180.dp),
        )
        DropletSegmented(
            options.map { it.second },
            options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
            { onSelect(options[it].first) },
            label = title,
            optionWidth = optionWidth.dp,
        )
    }
}

/** Categories the update check covers: tap once to include only those, twice to leave one out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateCategories(settingsStore: SettingsStore, categories: List<String>) {
    val settings by settingsStore.settings.collectAsState()
    if (categories.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Categories to check",
            style = MaterialTheme.typography.titleMedium,
            color = BeakoColors.Text,
        )
        Text(
            "Included: only those are checked (none included checks every category). Excluded: never " +
                "checked.",
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { name ->
                val state =
                    when (name) {
                        in settings.updateCategories -> TriState.INCLUDED
                        in settings.updateExcludedCategories -> TriState.EXCLUDED
                        else -> TriState.NONE
                    }
                TriStateChip(
                    name,
                    state,
                    {
                        settingsStore.update {
                            when (state) {
                                TriState.NONE ->
                                    it.copy(updateCategories = it.updateCategories + name)
                                TriState.INCLUDED ->
                                    it.copy(
                                        updateCategories = it.updateCategories - name,
                                        updateExcludedCategories =
                                            it.updateExcludedCategories + name,
                                    )
                                TriState.EXCLUDED ->
                                    it.copy(
                                        updateExcludedCategories =
                                            it.updateExcludedCategories - name,
                                    )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A folder picked in Windows' folder dialog, or null when cancelled. */
private fun chooseFolder(start: File): File? {
    val chooser =
        javax.swing.JFileChooser(start.takeIf { it.exists() } ?: start.parentFile).apply {
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose the local manga folder"
        }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION)
        chooser.selectedFile
    else null
}
