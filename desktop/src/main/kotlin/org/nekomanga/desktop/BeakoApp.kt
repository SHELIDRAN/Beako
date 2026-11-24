package org.nekomanga.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.nekomanga.desktop.components.AccountSlot
import org.nekomanga.desktop.components.DefaultAccountSlots
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.components.GlassIconButton
import org.nekomanga.desktop.components.Sidebar
import org.nekomanga.desktop.components.SidebarPanelReserve
import org.nekomanga.desktop.components.SidebarRailWidth
import org.nekomanga.desktop.state.LocalSearchShortcut
import org.nekomanga.desktop.state.Navigator
import org.nekomanga.desktop.state.Page
import org.nekomanga.desktop.state.SearchShortcut
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.LocalHazeState
import org.nekomanga.desktop.theme.SidebarMode

const val APP_NAME = "Beako"

/** Root of the desktop UI: the glass sidebar over the selected screen or the open page. */
@Composable
fun BeakoApp(
    appearance: BeakoAppearance = BeakoAppearance(),
    start: Destination = Destination.BROWSE,
    navigator: Navigator = remember { Navigator() },
    page: @Composable (Page) -> Unit = {},
    screen: @Composable (Destination) -> Unit = { PlaceholderScreen(it) },
    accounts: List<AccountSlot> = DefaultAccountSlots,
    onAccountClick: (String) -> Unit = {},
    /** Counts shown in the menu, e.g. new chapters on Latest updates. */
    counts: Map<Destination, Int> = emptyMap(),
    /**
     * When the newest unread update was found (epoch ms); newer than the last visit shows a dot.
     */
    newestUpdateAt: Long = 0L,
    /** Saves a new Sidebar setting (the menu's collapse and keep-open controls). */
    onSidebarMode: (SidebarMode) -> Unit = {},
) {
    var destination by remember { mutableStateOf(start) }
    var updatesSeenAt by remember { mutableStateOf(0L) }
    LaunchedEffect(destination, newestUpdateAt) {
        if (destination == Destination.UPDATES) updatesSeenAt = newestUpdateAt
    }
    navigator.requestedDestination?.let {
        destination = it
        navigator.requestedDestination = null
    }
    val openPage = navigator.top
    // The reader takes the whole window; every other page keeps the sidebar.
    val immersive = openPage is Page.Reader
    val haze = remember { HazeState() }
    val searchShortcut = remember { SearchShortcut() }
    BeakoTheme(appearance) {
        CompositionLocalProvider(LocalSearchShortcut provides searchShortcut) {
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(BeakoColors.GroundGlow, BeakoColors.Ground),
                            center = Offset(120f, 0f),
                            radius = 1400f,
                        ),
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val target = shortcutDestination(event.key)
                        when {
                            // Ctrl+F: this screen's search box, or Search when it has none.
                            event.isCtrlPressed && event.key == Key.F && !immersive -> {
                                if (!searchShortcut.focus()) {
                                    if (openPage !is Page.Search) navigator.open(Page.Search())
                                    destination = Destination.BROWSE
                                }
                                true
                            }
                            // Ctrl+K: the menu's search.
                            event.isCtrlPressed && event.key == Key.K && !immersive -> {
                                if (openPage !is Page.Search) navigator.open(Page.Search())
                                destination = Destination.BROWSE
                                true
                            }
                            event.isCtrlPressed && target != null && !immersive -> {
                                navigator.clear()
                                destination = target
                                true
                            }
                            // Alt+Left goes back from any page. (Esc is handled by each page.)
                            event.isAltPressed && event.key == Key.DirectionLeft -> navigator.back()
                            else -> false
                        }
                    },
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .hazeSource(haze)
                        .padding(
                            start =
                                when {
                                    immersive -> 0.dp
                                    appearance.sidebarMode == SidebarMode.ALWAYS_EXPANDED ->
                                        SidebarPanelReserve
                                    else -> SidebarRailWidth
                                },
                        ),
                ) {
                    AnimatedContent(
                        targetState = openPage to destination,
                        transitionSpec = { fadeThrough(appearance.reduceMotion) },
                    ) { (targetPage, targetDestination) ->
                        if (targetPage != null) page(targetPage) else screen(targetDestination)
                    }
                }
                if (!immersive) {
                    CompositionLocalProvider(LocalHazeState provides haze) {
                        Sidebar(
                            accounts = accounts,
                            onAccountClick = onAccountClick,
                            selected = destination,
                            onSelect = {
                                navigator.clear()
                                destination = it
                            },
                            searchOpen = openPage is Page.Search,
                            onSearch = {
                                if (openPage !is Page.Search) navigator.open(Page.Search())
                                destination = Destination.BROWSE
                            },
                            counts = counts,
                            fresh =
                                if (newestUpdateAt > updatesSeenAt) setOf(Destination.UPDATES)
                                else emptySet(),
                            onModeChange = onSidebarMode,
                            modifier =
                                Modifier.align(Alignment.CenterStart)
                                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/** Ctrl+1…5 jump to the sidebar's screens; Ctrl+, opens More (settings). */
private fun shortcutDestination(key: Key): Destination? =
    when (key) {
        Key.One -> Destination.LIBRARY
        Key.Two -> Destination.UPDATES
        Key.Three -> Destination.BROWSE
        Key.Four -> Destination.HISTORY
        Key.Five,
        Key.Comma -> Destination.MORE
        else -> null
    }

/** The outgoing screen fades out quickly, then the incoming one fades in. */
private fun fadeThrough(reduceMotion: Boolean) =
    if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
    else
        (fadeIn(tween(150, delayMillis = 60)) + scaleIn(tween(150, delayMillis = 60), 0.985f))
            .togetherWith(fadeOut(tween(60)))

@Composable
internal fun PlaceholderScreen(destination: Destination) {
    Box(Modifier.fillMaxSize().padding(32.dp)) {
        Text(
            destination.label,
            style = MaterialTheme.typography.displaySmall,
            color = BeakoColors.Text,
        )
    }
}

/** Stand-in for a page that isn't built yet, with a way back. */
@Composable
internal fun PlaceholderPage(name: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, swingFrom = -90f)
        Text(name, style = MaterialTheme.typography.displaySmall, color = BeakoColors.Text)
    }
}
