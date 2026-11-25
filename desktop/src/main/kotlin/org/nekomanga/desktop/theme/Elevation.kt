package org.nekomanga.desktop.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * The design system's shadows (Beako Glass `shadow-*` tokens). Light falls from the top-left, so
 * drops fall down-right. Porcelain gets lighter, softer versions.
 */
object ShadowSpec {
    private fun shadow(radius: Dp, color: Color, x: Dp = 0.dp, y: Dp = 0.dp, spread: Dp = 0.dp) =
        Shadow(radius = radius, color = color, spread = spread, offset = DpOffset(x, y))

    private val light: Boolean
        get() = BeakoColors.palette.light

    /** Floating glass (sidebar, panels): `shadow-glass`. */
    fun glass() =
        if (light) shadow(28.dp, Color(0x1F000000), y = 8.dp)
        else shadow(24.dp, Color(0xB3000000), y = 6.dp)

    /** The selection droplet: `shadow-droplet`. */
    fun droplet() =
        if (light) shadow(8.dp, Color(0x26000000), y = 2.dp)
        else shadow(12.dp, Color(0x80000000), y = 3.dp)

    /** Popovers and flyouts, higher than glass: `shadow-float`. */
    fun float() =
        listOf(
            if (light) shadow(40.dp, Color(0x2E000000), y = 16.dp, spread = (-10).dp)
            else shadow(40.dp, Color(0xCC000000), y = 16.dp, spread = (-8).dp),
            shadow(6.dp, if (light) Color(0x0F000000) else Color(0x66000000), y = 2.dp),
        )

    /** Soft raised: a light edge up-left and a dark drop down-right (`shadow-soft-raised`). */
    fun softRaised() =
        if (light)
            listOf(
                shadow(14.dp, Color(0xE6FFFFFF), x = (-6).dp, y = (-6).dp),
                shadow(16.dp, Color(0x1F000000), x = 6.dp, y = 6.dp),
            )
        else
            listOf(
                shadow(12.dp, Color(0x0AFFFFFF), x = (-4).dp, y = (-4).dp),
                shadow(16.dp, Color(0xB3000000), x = 6.dp, y = 6.dp),
            )

    /** Soft pressed in (`shadow-soft-inset`). */
    fun softInset() =
        if (light)
            listOf(
                shadow(8.dp, Color(0x1F000000), x = 3.dp, y = 3.dp),
                shadow(8.dp, Color(0xE6FFFFFF), x = (-3).dp, y = (-3).dp),
            )
        else
            listOf(
                shadow(8.dp, Color(0xB3000000), x = 3.dp, y = 3.dp),
                shadow(8.dp, Color(0x0AFFFFFF), x = (-3).dp, y = (-3).dp),
            )

    /**
     * A card at rest ([hover] 0) or lifted ([hover] 1): a tight contact shadow and a soft drop that
     * grows deeper and further down-right as the card rises (`shadow-card` → `shadow-card-hover`).
     */
    fun card(hover: Float): List<Shadow> {
        val strong = if (light) 0.16f else 0.7f
        val contact = if (light) 0.06f else 0.5f
        // Two layers (contact and drop): every card draws these, so they're kept cheap.
        return listOf(
            shadow(lerp(2.dp, 4.dp, hover), Color.Black.copy(alpha = contact), y = 1.dp),
            shadow(
                lerp(16.dp, 32.dp, hover),
                Color.Black.copy(alpha = strong * lerp(0.9f, 1f, hover)),
                x = lerp(4.dp, 7.dp, hover),
                y = lerp(8.dp, 20.dp, hover),
                spread = lerp((-4).dp, (-10).dp, hover),
            ),
        )
    }

    /** The orange glow under an accent button or alert (`shadow-accent-glow`). */
    fun accentGlow(alpha: Float = 1f) =
        shadow(20.dp, BeakoColors.AccentGlow.copy(alpha = 0.4f * alpha), y = 6.dp)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Draws [shadows] behind the content, in order. */
fun Modifier.dropShadows(shape: Shape, shadows: List<Shadow>): Modifier =
    shadows.fold(this) { m, s -> m.dropShadow(shape, s) }

/** Draws [shadows] inside the content's edge, in order. */
fun Modifier.innerShadows(shape: Shape, shadows: List<Shadow>): Modifier =
    shadows.fold(this) { m, s -> m.innerShadow(shape, s) }

/** The rim colour between two states, for borders that change with hover or selection. */
fun mix(from: Color, to: Color, t: Float): Color = lerp(from, to, t.coerceIn(0f, 1f))
