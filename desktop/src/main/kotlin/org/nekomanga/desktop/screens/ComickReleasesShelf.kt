package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.Shelf
import org.nekomanga.desktop.state.AccountsModel
import org.nekomanga.desktop.state.Loadable
import org.nekomanga.shared.model.Title

/**
 * "New in your Comick library": titles you're reading on Comick with chapters past your progress.
 * Stands in for followed collections, which Comick's API doesn't offer. Hidden when signed out.
 */
@Composable
fun ComickReleasesShelf(accounts: AccountsModel, onOpen: (Title) -> Unit) {
    LaunchedEffect(accounts) { accounts.loadComickReleases() }
    val releases = accounts.comickReleases ?: return
    val entries = (releases as? Loadable.Ready)?.value.orEmpty()
    val latest = entries.associate { it.title.id to it.latestNumber }
    Shelf(
        "New in your Comick library",
        when (releases) {
            is Loadable.Ready -> Loadable.Ready(entries.map { it.title })
            is Loadable.Failed ->
                Loadable.Failed("Couldn’t load your Comick library. ${releases.message}")
            Loadable.Loading -> Loadable.Loading
        },
        onOpen,
        Modifier.padding(bottom = 18.dp),
        inset = 0.dp,
        badge = { title -> latest[title.id]?.let { "Ch. $it" } },
    ) {
        GlassIconButton(
            Icons.Outlined.Refresh,
            "Refresh Comick library",
            { accounts.loadComickReleases(force = true) },
        )
    }
}
