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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.components.Sidebar
import org.nekomanga.desktop.components.SidebarRailWidth
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.BeakoTheme
import org.nekomanga.desktop.theme.LocalHazeState

const val APP_NAME = "Beako"

/** Root of the desktop UI: the glass sidebar over the selected screen. */
@Composable
fun BeakoApp(
    appearance: BeakoAppearance = BeakoAppearance(),
    start: Destination = Destination.BROWSE,
    screen: @Composable (Destination) -> Unit = { PlaceholderScreen(it) },
) {
    var destination by remember { mutableStateOf(start) }
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
                    val target = shortcutDestination(event.key)
                    if (
                        event.type == KeyEventType.KeyDown && event.isCtrlPressed && target != null
                    ) {
                        destination = target
                        true
                    } else false
                },
        ) {
            Box(Modifier.fillMaxSize().hazeSource(haze).padding(start = SidebarRailWidth)) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = { fadeThrough(appearance.reduceMotion) },
                ) { target ->
                    screen(target)
                }
            }
            CompositionLocalProvider(LocalHazeState provides haze) {
                Sidebar(
                    selected = destination,
                    onSelect = { destination = it },
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .fillMaxHeight(0.86f),
                )
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
private fun PlaceholderScreen(destination: Destination) {
    Box(Modifier.fillMaxSize().padding(32.dp)) {
        Text(
            destination.label,
            style = MaterialTheme.typography.displaySmall,
            color = BeakoColors.Text,
        )
    }
}
