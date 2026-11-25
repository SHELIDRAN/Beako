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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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

private val rimBrush: Brush
    get() = Brush.verticalGradient(listOf(BeakoColors.RimBright, BeakoColors.Rim))

/**
 * Neutral liquid glass: what is behind is blurred and saturated under a light dark tint, with a
 * thin bright rim. Solid when "reduce transparency" is on.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color = BeakoColors.GlassTint, blur: Dp = 24.dp): Modifier {
    val appearance = LocalAppearance.current
    val haze = LocalHazeState.current
    val strength = appearance.glassStrength.coerceIn(0.5f, 1.5f)
    val fill =
        when {
            appearance.reduceTransparency -> Modifier.background(BeakoColors.GlassSolid)
            haze != null ->
                Modifier.hazeEffect(
                    state = haze,
                    style =
                        HazeStyle(
                            backgroundColor = BeakoColors.Ground,
                            tints =
                                listOf(
                                    HazeTint(
                                        tint.copy(alpha = (tint.alpha * strength).coerceIn(0f, 1f)),
                                    ),
                                ),
                            blurRadius = blur * strength,
                            noiseFactor = 0f,
                        ),
                )
            else -> Modifier.background(tint)
        }
    return this.clip(shape).then(fill).border(1.dp, rimBrush, shape)
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
            modifier.then(
                if (elevation > 0.dp) Modifier.dropShadow(shape, ShadowSpec.glass()) else Modifier,
            ),
    ) {
        GlassBackdrop(shape, Modifier.matchParentSize())
        Box(Modifier.clip(shape), content = content)
    }
}

/**
 * The glass behind large panels: the blurred backdrop drawn on its own layer with the saturation
 * boost liquid glass calls for, so colour behind the panel stays vivid instead of washing out. Text
 * and icons are drawn above it, untouched. Haze has no saturation setting, so the layer applies a
 * colour matrix to what Haze draws.
 */
@Composable
fun GlassBackdrop(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = BeakoColors.GlassTint,
    blur: Dp = 24.dp,
) {
    val appearance = LocalAppearance.current
    val saturate = !appearance.reduceTransparency && LocalHazeState.current != null
    Box(
        modifier
            .then(
                if (saturate) Modifier.graphicsLayer { renderEffect = SaturationEffect }
                else Modifier,
            )
            .glass(shape, tint, blur),
    )
}

/** How much the backdrop's colour is boosted under large glass (1 = unchanged). */
const val GLASS_SATURATION = 1.6f

private val SaturationEffect: RenderEffect by lazy {
    val s = GLASS_SATURATION
    // Rec. 709 luma weights: the boost leaves greys grey.
    val r = 0.2126f * (1 - s)
    val g = 0.7152f * (1 - s)
    val b = 0.0722f * (1 - s)
    val matrix =
        org.jetbrains.skia.ColorMatrix(
            r + s,
            g,
            b,
            0f,
            0f,
            r,
            g + s,
            b,
            0f,
            0f,
            r,
            g,
            b + s,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )
    org.jetbrains.skia.ImageFilter.makeColorFilter(
            org.jetbrains.skia.ColorFilter.makeMatrix(matrix),
            null,
            null,
        )
        .asComposeRenderEffect()
}

/**
 * The clear glass droplet that marks a selection: mostly transparent, faint rim, soft shadow and no
 * bright glint.
 */
@Composable
fun Droplet(modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(
        modifier
            .dropShadow(shape, ShadowSpec.droplet())
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(BeakoColors.DropletTop, BeakoColors.DropletBottom)),
            )
            .border(1.dp, BeakoColors.DropletTop, shape),
    )
}

/**
 * The droplet's slide: a soft spring, or instant with reduce motion. Its bounce and speed follow
 * the Motion setting; [stiffness] is lower for the slower Swing (about 520 ms when Lively).
 */
@Composable
fun <T> dropletSpec(stiffness: Float = Spring.StiffnessMediumLow): AnimationSpec<T> {
    val appearance = LocalAppearance.current
    if (appearance.reduceMotion) return snap()
    val feel = appearance.motion
    // Lively keeps the given stiffness; calmer feels scale it down for a slower glide.
    val scaled = stiffness * feel.stiffness / MotionFeel.LIVELY.stiffness
    return spring(dampingRatio = feel.damping, stiffness = scaled)
}
