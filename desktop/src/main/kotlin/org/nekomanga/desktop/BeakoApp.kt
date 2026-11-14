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
import org.nekomanga.desktop.components.SidebarRailWidth
import org.nekomanga.desktop.state.Navigator
import org.nekomanga.desktop.state.Page
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.LocalHazeState

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
) {
    var destination by remember { mutableStateOf(start) }
    navigator.requestedDestination?.let {
        destination = it
        navigator.requestedDestination = null
    }
    val openPage = navigator.top
    // The reader takes the whole window; every other page keeps the sidebar.
    val immersive = openPage is Page.Reader
    val haze = remember { HazeState() }
    BeakoTheme(appearance) {
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
                    .padding(start = if (immersive) 0.dp else SidebarRailWidth),
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
                        modifier =
                            Modifier.align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .fillMaxHeight(0.86f),
                    )
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
        GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Text(name, style = MaterialTheme.typography.displaySmall, color = BeakoColors.Text)
    }
}
