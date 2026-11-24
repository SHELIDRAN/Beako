package org.nekomanga.desktop.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The motion vocabulary of Beako Glass: Press, Sweep, Rise, Swing, Travel and Bloom. One move per
 * interaction, nothing loops, and Reduce motion makes every one instant.
 */
object Motion {
    /** `cubic-bezier(.65,0,.35,1)`: the in-out curve of Sweep and Rise. */
    val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    /** `cubic-bezier(.2,.7,.2,1)`: the ease-out of shadows and fades. */
    val Out = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

    const val PRESS_DOWN = 90
    const val PRESS_UP = 220
    const val SWEEP = 380
    const val RISE = 380
    const val SHADOW = 450
}

/** A tween on the Sweep/Rise curve, or instant with Reduce motion. */
@Composable
fun <T> sweepSpec(durationMillis: Int = Motion.SWEEP, delayMillis: Int = 0): AnimationSpec<T> =
    if (LocalAppearance.current.reduceMotion) snap()
    else tween(durationMillis, delayMillis, Motion.InOut)

/** An ease-out tween (shadows, glows), instant with Reduce motion. */
@Composable
fun <T> easeOutSpec(durationMillis: Int = Motion.SHADOW): AnimationSpec<T> =
    if (LocalAppearance.current.reduceMotion) snap() else tween(durationMillis, easing = Motion.Out)

/** Press: whatever is held dips to 96% (90 ms down, 220 ms back). */
@Composable
fun Modifier.press(interaction: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalAppearance.current.reduceMotion
    val down = pressed && enabled
    val scale by
        animateFloatAsState(
            if (down) 0.96f else 1f,
            if (reduceMotion) snap()
            else tween(if (down) Motion.PRESS_DOWN else Motion.PRESS_UP, easing = Motion.Out),
            label = "press",
        )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
