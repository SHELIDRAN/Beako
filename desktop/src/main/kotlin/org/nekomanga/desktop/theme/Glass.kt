package org.nekomanga.desktop.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The blur source glass panels sample from. Null (the default) means no content is registered, and
 * glass falls back to a translucent fill.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

private val RimBrush = Brush.verticalGradient(listOf(BeakoColors.RimBright, BeakoColors.Rim))

/**
 * Neutral liquid glass: what is behind is blurred and saturated under a light dark tint, with a
 * thin bright rim. Solid when "reduce transparency" is on.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color = BeakoColors.GlassTint, blur: Dp = 24.dp): Modifier {
    val appearance = LocalAppearance.current
    val haze = LocalHazeState.current
    val fill =
        when {
            appearance.reduceTransparency -> Modifier.background(BeakoColors.GlassSolid)
            haze != null ->
                Modifier.hazeEffect(
                    state = haze,
                    style =
                        HazeStyle(
                            backgroundColor = BeakoColors.Ground,
                            tints = listOf(HazeTint(tint)),
                            blurRadius = blur,
                            noiseFactor = 0f,
                        ),
                )
            else -> Modifier.background(tint)
        }
    return this.clip(shape).then(fill).border(1.dp, RimBrush, shape)
}

@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .glass(shape),
        content = content,
    )
}

/**
 * The clear glass droplet that marks a selection: mostly transparent, faint rim, soft shadow and no
 * bright glint.
 */
@Composable
fun Droplet(modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(
        modifier
            .shadow(6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x3DFFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, Color(0x3DFFFFFF), shape),
    )
}

/** The droplet's slide: a soft spring with a little overshoot, or instant with reduce motion. */
@Composable
fun <T> dropletSpec(): AnimationSpec<T> =
    if (LocalAppearance.current.reduceMotion) snap()
    else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
