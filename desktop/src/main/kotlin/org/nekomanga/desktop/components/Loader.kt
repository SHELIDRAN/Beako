package org.nekomanga.desktop.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.desktop.theme.LocalAppearance

/**
 * Beako's loading mark: a small blob that turns and morphs, circle → cookie → pentagon → clover and
 * round again, in the scheme's accent on a soft accent disc. With Reduce motion it holds still and
 * only breathes in opacity. [label] is read by screen readers ("Loading chapter 12").
 */
@Composable
fun BeakoLoader(modifier: Modifier = Modifier, size: Dp = 48.dp, label: String = "Loading") {
    val reduceMotion = LocalAppearance.current.reduceMotion
    val time = rememberInfiniteTransition(label = "loader")
    // One full morph cycle through the four shapes.
    val morph by
        time.animateFloat(
            0f,
            SHAPES.size.toFloat(),
            infiniteRepeatable(tween(if (reduceMotion) 1 else 4_800, easing = LinearEasing)),
            label = "morph",
        )
    val spin by
        time.animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(if (reduceMotion) 1 else 2_400, easing = LinearEasing)),
            label = "spin",
        )
    val breathe by
        time.animateFloat(
            0.55f,
            1f,
            infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "breathe",
        )
    val accent = BeakoColors.Accent
    val disc = BeakoColors.AccentSoft.copy(alpha = 0.5f)
    val path = remember { Path() }
    Canvas(modifier.size(size).semantics { contentDescription = label }) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val outer = this.size.minDimension / 2
        drawCircle(disc, outer, center)
        val radius = outer * 0.58f
        val phase = if (reduceMotion) 0f else morph
        val from = SHAPES[phase.toInt() % SHAPES.size]
        val to = SHAPES[(phase.toInt() + 1) % SHAPES.size]
        // Ease each step so a shape rests a moment before flowing into the next.
        val t = smooth(phase - phase.toInt())
        path.reset()
        for (i in 0..POINTS) {
            val angle = (i % POINTS) * (2 * PI / POINTS)
            val r = radius * (from(angle) + (to(angle) - from(angle)) * t)
            val x = center.x + r * cos(angle).toFloat()
            val y = center.y + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        rotate(if (reduceMotion) 0f else spin, center) {
            drawPath(path, accent.copy(alpha = if (reduceMotion) breathe else 1f))
        }
    }
}

/** A loader with a short line under it, for a whole screen or panel that's loading. */
@Composable
fun LoadingMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BeakoLoader(label = text)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = BeakoColors.TextMuted)
    }
}

private const val POINTS = 96

private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

/** The shapes, as radius (1 = nominal) at each angle. */
private val SHAPES: List<(Double) -> Float> =
    listOf(
        // Circle.
        { _ -> 1f },
        // Cookie: eight soft scallops.
        { a -> (1f + 0.09f * cos(8 * a).toFloat()) },
        // Rounded pentagon.
        { a -> roundedPolygon(a, 5, 0.86f) },
        // Clover: four round leaves.
        { a -> (0.84f + 0.2f * abs(cos(2 * a)).toFloat().pow(0.6f)) },
    )

/** Radius of a regular [sides]-gon at angle [a], softened toward a circle by [round] (0–1). */
private fun roundedPolygon(a: Double, sides: Int, round: Float): Float {
    val sector = 2 * PI / sides
    val local = ((a + PI / 2) % sector + sector) % sector - sector / 2
    val polygon = (cos(PI / sides) / cos(local)).toFloat()
    return (polygon * (1 - round * 0.35f) + round * 0.35f) * 0.98f
}
