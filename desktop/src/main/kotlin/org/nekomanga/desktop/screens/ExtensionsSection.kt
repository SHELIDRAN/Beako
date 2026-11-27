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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassTextField
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.state.ExtensionsModel
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.desktop.state.openInBrowser
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.extensions.ExtensionInfo
import org.nekomanga.shared.extensions.isRunnable

/** Where to find repositories: the owner's chosen directory. */
private const val DIRECTORY = "https://wotaku.wiki/ext/mangayomi"

/** More → Sources: add repositories, install sources, keep them updated. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtensionsSection(
    model: ExtensionsModel,
    /** The language shown ("" for all) and whether to sort by language, kept in settings. */
    language: String = "",
    byLanguage: Boolean = false,
    onLanguage: (String) -> Unit = {},
    onByLanguage: (Boolean) -> Unit = {},
) {
    LaunchedEffect(model) { model.start() }
    val data by model.manager.data.collectAsState()
    var repoText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<ExtensionInfo, String>?>(null) }

    Section("Sources") {
        Text(
            "Beako reads chapters from MangaDex and from sources you install. Sources are third-party code from " +
                "repositories you add: Beako ships none and doesn’t review them. They run in a sandbox that can only " +
                "load web pages; they can’t open your files or reach your local network.",
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
        GlassButton(
            "Find repositories on wotaku.wiki",
            { openInBrowser(DIRECTORY) },
            icon = Icons.Outlined.OpenInNew,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassTextField(
                "Repository link (index.json or a Mangayomi add-repo link)",
                repoText,
                { repoText = it },
                Modifier.weight(1f),
            )
            GlassButton(
                "Add",
                {
                    model.addRepository(repoText)
                    repoText = ""
                },
                icon = Icons.Outlined.Add,
                primary = true,
            )
        }
        model.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (model.isError) BeakoColors.Danger else BeakoColors.TextMuted,
            )
        }

        if (data.installed.isNotEmpty()) {
            HorizontalDivider(color = BeakoColors.Rim)
            Text(
                "Installed",
                style = MaterialTheme.typography.titleMedium,
                color = BeakoColors.Text,
            )
            LanguageGrouped(
                data.installed
                    .filter { language.isEmpty() || it.info.lang.equals(language, true) }
                    .sortedWith(sourceOrder(byLanguage) { it.info }),
                byLanguage,
                { it.info.lang },
            ) { source ->
                val update = model.updates[source.info.id]
                SourceRow(
                    source.info,
                    detail = "v${source.info.version} · ${source.info.lang.uppercase()}",
                ) {
                    if (update != null) {
                        GlassButton(
                            "Update to v${update.first.version}",
                            { model.install(update.first, update.second) },
                        )
                    }
                    GlassButton(
                        if (source.enabled) "Turn off" else "Turn on",
                        { model.manager.setEnabled(source.info.id, !source.enabled) },
                    )
                    GlassButton("Remove", { model.uninstall(source.info.id) })
                }
            }
        }

        data.repositories.forEach { repo ->
            HorizontalDivider(color = BeakoColors.Rim)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    repo,
                    style = MaterialTheme.typography.titleSmall,
                    color = BeakoColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GlassButton("Refresh", model::refresh, icon = Icons.Outlined.Refresh)
                GlassButton("Remove repository", { model.removeRepository(repo) })
            }
            when (val sources = model.catalog[repo]) {
                null,
                Loadable.Loading ->
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = BeakoColors.TextMuted,
                    )
                is Loadable.Failed ->
                    Text(
                        sources.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = BeakoColors.Danger,
                    )
                is Loadable.Ready -> {
                    val visible = sources.value.filter { data.showNsfw || !it.isNsfw }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchField(
                            filter,
                            { filter = it },
                            "Filter sources by name or language",
                            Modifier.width(360.dp),
                        )
                        // Languages this repository offers, most sources first.
                        val languages =
                            visible
                                .groupingBy { it.lang.lowercase() }
                                .eachCount()
                                .entries
                                .sortedByDescending { it.value }
                        Menu(
                            "Language: ${if (language.isEmpty()) "All" else languageName(language)}",
                            listOf("All languages · ${visible.size}") +
                                languages.map { (code, n) -> "${languageName(code)} · $n" },
                        ) { index ->
                            onLanguage(if (index == 0) "" else languages[index - 1].key)
                        }
                        Menu(
                            "Sort: ${if (byLanguage) "Language" else "Name"}",
                            listOf("Name", "Language"),
                        ) {
                            onByLanguage(it == 1)
                        }
                    }
                    val shown =
                        visible
                            .filter { language.isEmpty() || it.lang.equals(language, true) }
                            .filter {
                                filter.isBlank() ||
                                    it.name.contains(filter, true) ||
                                    it.lang.equals(filter, true) ||
                                    languageName(it.lang).contains(filter, true)
                            }
                            .sortedWith(sourceOrder(byLanguage) { it })
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LanguageGrouped(shown, byLanguage, { it.lang }) { info ->
                            val installed = data.installed.any { it.info.id == info.id }
                            SourceRow(
                                info,
                                detail =
                                    listOfNotNull(
                                            info.lang.uppercase(),
                                            "v${info.version}",
                                            when {
                                                info.isJavaScript -> "JavaScript"
                                                info.isRunnable ->
                                                    if (info.typeSource.equals("single", true))
                                                        "Dart, ported"
                                                    else
                                                        "${info.typeSource.replaceFirstChar { it.uppercase() }} template"
                                                else -> "Dart (not supported yet)"
                                            },
                                            "18+".takeIf { info.isNsfw },
                                        )
                                        .joinToString(" · "),
                            ) {
                                when {
                                    installed ->
                                        Text(
                                            "Installed",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = BeakoColors.TextMuted,
                                        )
                                    !info.isRunnable -> Unit
                                    model.busy[info.id] == true ->
                                        Text(
                                            "Installing…",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = BeakoColors.TextMuted,
                                        )
                                    else -> GlassButton("Install", { confirm = info to repo })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (data.repositories.isNotEmpty()) {
            ToggleRow(
                "Show 18+ sources",
                "List sources marked adult in their repository.",
                data.showNsfw,
            ) {
                model.manager.setShowNsfw(it)
            }
        }
    }

    confirm?.let { (info, repo) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Install ${info.name}?") },
            text = {
                Text(
                    "This source is third-party code from $repo. Beako runs it in a sandbox that can only load web " +
                        "pages, but it can see what you search for and read on ${info.baseUrl.ifBlank { "its site" }}. " +
                        "Install sources only from repositories you trust.",
                )
            },
            confirmButton = {
                GlassButton(
                    "Install",
                    {
                        model.install(info, repo)
                        confirm = null
                    },
                    primary = true,
                )
            },
            dismissButton = { GlassButton("Cancel", { confirm = null }) },
            containerColor = BeakoColors.SurfaceRaised,
            titleContentColor = BeakoColors.Text,
            textContentColor = BeakoColors.TextMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(info: ExtensionInfo, detail: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(info.name, style = MaterialTheme.typography.titleMedium, color = BeakoColors.Text)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = BeakoColors.TextMuted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/**
 * Sources in order: by name (runnable first, copies marked "unoriginal" last), or with [byLanguage]
 * by language first, in the order of the languages' names.
 */
private fun <T> sourceOrder(byLanguage: Boolean, info: (T) -> ExtensionInfo): Comparator<T> {
    val byName =
        compareBy<T>(
            { !info(it).isRunnable },
            { info(it).isUnoriginal },
            { info(it).name.lowercase() },
        )
    return if (byLanguage) compareBy<T> { languageName(info(it).lang) }.then(byName) else byName
}

/** [items] as they are, or under a heading for each language when [byLanguage] is on. */
@Composable
private fun <T> LanguageGrouped(
    items: List<T>,
    byLanguage: Boolean,
    lang: (T) -> String,
    row: @Composable (T) -> Unit,
) {
    var previous: String? = null
    items.forEach { item ->
        val name = languageName(lang(item))
        if (byLanguage && name != previous) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = BeakoColors.TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            previous = name
        }
        row(item)
    }
}

/** "English", "Portuguese (Brazil)", "Multiple languages" for Mangayomi's language codes. */
internal fun languageName(code: String): String =
    when (code.trim().lowercase()) {
        "",
        "all",
        "multi" -> "Multiple languages"
        else ->
            java.util.Locale.forLanguageTag(code.trim())
                .getDisplayName(java.util.Locale.ENGLISH)
                .ifBlank { code.uppercase() }
    }
