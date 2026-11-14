package org.nekomanga.desktop.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.io.File
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.state.DohProvider
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.Palette
import org.nekomanga.desktop.theme.glass
import org.nekomanga.shared.download.DownloadManager
import org.nekomanga.shared.download.DownloadState
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.Settings
import org.nekomanga.shared.library.SettingsStore

/** The More screen: stats, modes, the download queue and appearance settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    settingsStore: SettingsStore,
    library: LibraryStore,
    downloads: DownloadManager,
    downloadsFolder: File,
    accounts: (@Composable () -> Unit)? = null,
    backup: (@Composable () -> Unit)? = null,
    sources: (@Composable () -> Unit)? = null,
) {
    val settings by settingsStore.settings.collectAsState()
    val data by library.data.collectAsState()
    val queue by downloads.queue.collectAsState()
    var paused by remember { mutableStateOf(downloads.paused) }
    val chaptersRead = remember(data) { data.entries.sumOf { it.readChapterIds.size } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("title") {
            Text("More", style = MaterialTheme.typography.displaySmall, color = BeakoColors.Text)
        }
        item("stats") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatTile("${data.entries.size}", "Series")
                StatTile("$chaptersRead", "Chapters read")
                StatTile("${data.categories.size}", "Categories")
            }
        }
        if (accounts != null) item("accounts") { accounts() }
        item("modes") {
            Section("Modes") {
                ToggleRow(
                    "Downloaded only",
                    "Show only titles with downloaded chapters in the library.",
                    settings.downloadedOnly,
                ) {
                    settingsStore.update { s -> s.copy(downloadedOnly = it) }
                }
                ToggleRow(
                    "Incognito mode",
                    "Pause reading history and progress.",
                    settings.incognito,
                ) {
                    settingsStore.update { s -> s.copy(incognito = it) }
                }
            }
        }
        item("appearance") {
            Section("Appearance") {
                ToggleRow(
                    "Reduce motion",
                    "Make transitions and slides instant.",
                    settings.reduceMotion,
                ) {
                    settingsStore.update { s -> s.copy(reduceMotion = it) }
                }
                ToggleRow(
                    "Reduce transparency",
                    "Use solid panels instead of glass.",
                    settings.reduceTransparency,
                ) {
                    settingsStore.update { s -> s.copy(reduceTransparency = it) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Sidebar",
                        style = MaterialTheme.typography.titleMedium,
                        color = BeakoColors.Text,
                        modifier = Modifier.width(180.dp),
                    )
                    Menu(
                        sidebarLabel(settings.sidebarMode),
                        SidebarModes.map { it.second },
                    ) { index ->
                        settingsStore.update { it.copy(sidebarMode = SidebarModes[index].first) }
                    }
                }
                PaletteRow(settings.palette) { name ->
                    settingsStore.update { it.copy(palette = name) }
                }
                SliderRow(
                    "Glass strength",
                    "${(settings.glassStrength * 100).toInt()}%",
                    settings.glassStrength,
                    0.5f..1.5f,
                ) { value ->
                    settingsStore.update { it.copy(glassStrength = value) }
                }
                SliderRow(
                    "Interface size",
                    "${(settings.uiScale * 100).toInt()}%",
                    settings.uiScale,
                    0.8f..1.5f,
                    steps = 6,
                ) { value ->
                    settingsStore.update { it.copy(uiScale = value) }
                }
            }
        }
        item("general") {
            Section("Library updates and network") {
                LabeledMenu(
                    "Check for new chapters",
                    UpdateIntervals.firstOrNull { it.first == settings.updateIntervalHours }?.second
                        ?: "Every ${settings.updateIntervalHours} hours",
                    UpdateIntervals.map { it.second },
                ) { index ->
                    settingsStore.update {
                        it.copy(updateIntervalHours = UpdateIntervals[index].first)
                    }
                }
                SkipRules.forEach { (rule, title, detail) ->
                    ToggleRow(title, detail, rule in settings.updateSkip) { on ->
                        settingsStore.update {
                            it.copy(
                                updateSkip = if (on) it.updateSkip + rule else it.updateSkip - rule,
                            )
                        }
                    }
                }
                LabeledMenu(
                    "DNS over HTTPS",
                    DohProvider.of(settings.dnsOverHttps)?.label ?: "Off (use Windows DNS)",
                    listOf("Off (use Windows DNS)") + DohProvider.entries.map { it.label },
                ) { index ->
                    settingsStore.update {
                        it.copy(
                            dnsOverHttps =
                                if (index == 0) "" else DohProvider.entries[index - 1].name,
                        )
                    }
                }
                Text(
                    "DNS over HTTPS can help when your network blocks a site. It applies to new connections at once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }
        }
        item("reading") {
            Section("Reading and downloads") {
                ToggleRow(
                    "Data saver",
                    "Read and download MangaDex’s smaller, compressed images.",
                    settings.dataSaver,
                ) {
                    settingsStore.update { s -> s.copy(dataSaver = it) }
                }
            }
        }
        item("queue-head") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (queue.isEmpty()) "Download queue" else "Download queue · ${queue.size}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = BeakoColors.Text,
                    modifier = Modifier.weight(1f),
                )
                if (queue.isNotEmpty()) {
                    GlassButton(
                        if (paused) "Resume" else "Pause",
                        onClick = {
                            if (paused) downloads.resume() else downloads.pause()
                            paused = downloads.paused
                        },
                        icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    )
                    if (queue.any { it.state == DownloadState.FAILED }) {
                        GlassButton("Clear failed", onClick = downloads::clearFailed)
                    }
                }
                GlassButton(
                    "Open downloads folder",
                    onClick = { openFolder(downloadsFolder) },
                    icon = Icons.Outlined.FolderOpen,
                )
            }
        }
        if (queue.isEmpty()) {
            item("queue-empty") {
                Text(
                    "Nothing downloading. Download chapters from a title’s page, or right-click a chapter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.TextMuted,
                )
            }
        }
        items(queue, key = { it.key }) { job ->
            Row(
                Modifier.fillMaxWidth()
                    .glass(RoundedCornerShape(14.dp), tint = BeakoColors.Rim)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${job.title.title} · ${job.chapter.displayName()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = BeakoColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when (job.state) {
                        DownloadState.FAILED ->
                            Text(
                                "Failed: ${job.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BeakoColors.Danger,
                            )
                        DownloadState.DOWNLOADING ->
                            LinearProgressIndicator(
                                progress = {
                                    if (job.total == 0) 0f else job.done.toFloat() / job.total
                                },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = BeakoColors.Text,
                                trackColor = BeakoColors.Rim,
                            )
                        DownloadState.QUEUED ->
                            Text(
                                "Waiting",
                                style = MaterialTheme.typography.bodySmall,
                                color = BeakoColors.TextMuted,
                            )
                    }
                }
                GlassIconButton(Icons.Outlined.Close, "Cancel download", { downloads.cancel(job) })
            }
        }
        if (sources != null) item("sources") { sources() }
        item("keyboard") { KeyboardSection(settingsStore) }
        item("discord") { DiscordSection(settingsStore) }
        if (backup != null) item("backup") { backup() }
        item("about") {
            Section("About") {
                Text(
                    "Beako ${System.getProperty("beako.version") ?: "dev"} — a manga reader for Windows, based on Neko (Apache-2.0). " +
                        "Discovery by Comick and MangaDex. Beako isn’t affiliated with either.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BeakoColors.TextMuted,
                )
            }
        }
    }
}

private val SidebarModes =
    listOf(
        "AUTO_COLLAPSE" to "Auto-collapse",
        "ALWAYS_EXPANDED" to "Always expanded",
        "ICONS_ONLY" to "Icons only",
    )

private fun sidebarLabel(mode: String) =
    SidebarModes.firstOrNull { it.first == mode }?.second ?: "Auto-collapse"

internal fun openFolder(folder: File) {
    runCatching {
        folder.mkdirs()
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(folder)
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Column(
        Modifier.width(200.dp)
            .glass(RoundedCornerShape(18.dp), tint = BeakoColors.Rim)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = BeakoColors.Text)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = BeakoColors.TextMuted)
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), tint = BeakoColors.Rim)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = BeakoColors.Text)
        content()
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = BeakoColors.Text)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = BeakoColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = BeakoColors.Ground,
                    checkedTrackColor = BeakoColors.Text,
                    uncheckedThumbColor = BeakoColors.TextMuted,
                    uncheckedTrackColor = BeakoColors.Surface,
                    uncheckedBorderColor = BeakoColors.RimBright,
                ),
        )
    }
}

/** For tests: the label a sidebar mode shows. */
internal fun Settings.sidebarText() = sidebarLabel(sidebarMode)

@Composable
private fun PaletteRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Colour scheme",
            style = MaterialTheme.typography.titleMedium,
            color = BeakoColors.Text,
            modifier = Modifier.width(180.dp),
        )
        Palette.entries.forEach { palette ->
            val on = palette.name == selected
            Box(
                Modifier.size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(palette.groundGlow, palette.ground)),
                    )
                    .border(
                        if (on) 2.dp else 1.dp,
                        if (on) BeakoColors.Text else BeakoColors.RimBright,
                        CircleShape,
                    )
                    .clickable(role = Role.RadioButton) { onSelect(palette.name) }
                    .semantics {
                        contentDescription = palette.label
                        semanticsSelected = on
                    },
            )
        }
        Text(
            Palette.of(selected).label,
            style = MaterialTheme.typography.bodyMedium,
            color = BeakoColors.TextMuted,
        )
    }
}

/** A slider that previews while dragging and saves when released. */
@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 9,
    onChange: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableStateOf(value) }
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
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange((dragging * 100).toInt() / 100f) },
            valueRange = range,
            steps = steps,
            colors =
                SliderDefaults.colors(
                    thumbColor = BeakoColors.Text,
                    activeTrackColor = BeakoColors.Text,
                    inactiveTrackColor = BeakoColors.Rim,
                ),
            modifier =
                Modifier.widthIn(max = 320.dp).weight(1f).semantics { contentDescription = title },
        )
        Text(valueText, style = MaterialTheme.typography.bodyMedium, color = BeakoColors.TextMuted)
    }
}

@Composable
private fun LabeledMenu(
    title: String,
    current: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
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
        Menu(current, options, onSelect)
    }
}

private val UpdateIntervals =
    listOf(
        0 to "Never (only when I ask)",
        3 to "Every 3 hours",
        6 to "Every 6 hours",
        12 to "Every 12 hours",
        24 to "Once a day",
    )

private val SkipRules =
    listOf(
        Triple(
            "COMPLETED",
            "Skip finished series",
            "Leave out completed series you’ve read to the end.",
        ),
        Triple(
            "UNREAD",
            "Skip series with unread chapters",
            "Check only series you’re caught up on.",
        ),
        Triple(
            "NOT_STARTED",
            "Skip series you haven’t started",
            "Leave out titles with nothing read yet.",
        ),
    )
