package org.nekomanga.desktop.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.Banner
import org.nekomanga.desktop.components.DropletSegmented
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.SearchField
import org.nekomanga.desktop.components.Shelf
import org.nekomanga.desktop.state.BrowseModel
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.model.Title

/** The Browse home: a search field over Comick's charts. */
@Composable
fun BrowseScreen(
    model: BrowseModel,
    onSearch: () -> Unit,
    onOpen: (Title) -> Unit,
    /** Manga in the local folder; null when local manga is off. */
    localTitles: (suspend () -> List<Title>)? = null,
) {
    LaunchedEffect(model) { model.start() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Browse", style = MaterialTheme.typography.displaySmall, color = BeakoColors.Text)
            // Looks like the search field; clicking it opens the Search page.
            Box(Modifier.widthIn(max = 560.dp)) {
                SearchField("", {}, "Search titles, tags and genres", Modifier.fillMaxWidth())
                Box(
                    Modifier.matchParentSize()
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Search", onClick = onSearch)
                        .semantics { contentDescription = "Open search" },
                )
            }
            if (model.comickDown) {
                Banner("Comick is unavailable right now, so these shelves come from MangaDex.") {
                    GlassButton("Try Comick again", onClick = model::refresh)
                }
            }
        }
        if (localTitles != null) {
            var local by remember {
                androidx.compose.runtime.mutableStateOf<
                    org.nekomanga.desktop.state.Loadable<List<Title>>,
                >(
                    org.nekomanga.desktop.state.Loadable.Loading,
                )
            }
            LaunchedEffect(Unit) {
                local =
                    org.nekomanga.desktop.state.Loadable.Ready(
                        runCatching { localTitles() }.getOrDefault(emptyList()),
                    )
            }
            // Only shown once there's something in the folder.
            val ready = local as? org.nekomanga.desktop.state.Loadable.Ready
            if (ready != null && ready.value.isNotEmpty()) Shelf("On this PC", local, onOpen)
        }
        if (model.comickDown) {
            Shelf("Most followed on MangaDex", model.mangaDexPopular, onOpen, ranked = true)
            Shelf("Latest uploads on MangaDex", model.mangaDexLatest, onOpen)
        } else {
            Shelf(
                "Most Followed New Comics",
                model.mostFollowedNew,
                onOpen,
                ranked = true,
                trailing = {
                    DropletSegmented(
                        options = model.windows.map { it.second },
                        selectedIndex = model.windowIndex,
                        onSelect = model::selectWindow,
                        label = "Ranking window",
                        optionWidth = 52.dp,
                    )
                },
            )
            Shelf(
                "Most Recent Popular",
                model.recentPopular,
                onOpen,
                ranked = true,
                trailing = {
                    DropletSegmented(
                        options = model.windows.map { it.second },
                        selectedIndex = model.popularWindowIndex,
                        onSelect = model::selectPopularWindow,
                        label = "Popular window",
                        optionWidth = 52.dp,
                    )
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
