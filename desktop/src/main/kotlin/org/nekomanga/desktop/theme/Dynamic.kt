package org.nekomanga.desktop.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.concurrent.ConcurrentHashMap

/**
 * The Windows accent colour (Settings → Personalization → Colors), which the Dynamic schemes are
 * built from. Windows' default blue until it's read, and off Windows.
 */
object WindowsAccent {
    val DEFAULT = Color(0xFF0078D4)

    var color: Color by mutableStateOf(DEFAULT)
        private set

    fun update(value: Color) {
        if (value == color) return
        color = value
        // Colours derived from the old accent (error, rating…) are worked out again.
        BeakoColors.forgetDerived()
    }
}

/** The colours of a Dynamic scheme, built from one accent. */
internal class DynamicColors(
    val ground: Color,
    val glow: Color,
    val surface: Color,
    val raised: Color,
    val glass: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentFill: Color,
    val accentInk: Color,
    val secondary: Color,
)

/**
 * Builds a Dynamic scheme the way Neko's follows the wallpaper: neutral layers lightly tinted with
 * the accent, text tinted a touch, and the accent itself moved only as far as contrast requires
 * (accent 3:1 on the ground, secondary text 4.5:1, text 7:1, button labels 4.5:1). Any accent
 * works, from pale yellow to near-black navy.
 */
internal object DynamicScheme {
    private val built = ConcurrentHashMap<Pair<Color, Boolean>, DynamicColors>()

    fun of(accent: Color, light: Boolean): DynamicColors =
        built.getOrPut(accent to light) { build(accent, light) }

    private fun build(a: Color, light: Boolean): DynamicColors {
        val black = Color.Black
        val white = Color.White
        return if (light) {
            val ground = lerp(Color(0xFFEBEBEE), a, 0.08f)
            val surface = lerp(Color.White, a, 0.03f)
            val raised = lerp(Color(0xFFF4F4F6), a, 0.05f)
            val glass = lerp(surface, ground, 0.35f)
            val grounds = listOf(ground, surface, raised, glass)
            val accent = readable(a, listOf(ground, surface), black, 3.0)
            val (fill, ink) = button(a)
            DynamicColors(
                ground = ground,
                glow = lerp(Color(0xFFFAFAFC), a, 0.05f),
                surface = surface,
                raised = raised,
                glass = glass,
                text = readable(lerp(Color(0xFF17171A), a, 0.08f), grounds, black, 7.0),
                muted = readable(lerp(Color(0xFF55565E), a, 0.15f), grounds, black, 4.6),
                accent = accent,
                accentFill = fill,
                accentInk = ink,
                secondary = lerp(accent, white, 0.45f),
            )
        } else {
            val ground = lerp(Color(0xFF0E0F11), a, 0.06f)
            val surface = lerp(Color(0xFF17181B), a, 0.08f)
            val raised = lerp(Color(0xFF202125), a, 0.10f)
            val glass = lerp(surface, raised, 0.5f)
            val grounds = listOf(ground, surface, raised, glass)
            val accent = readable(a, listOf(ground, surface), white, 3.0)
            val (fill, ink) = button(a)
            DynamicColors(
                ground = ground,
                glow = lerp(ground, a, 0.14f),
                surface = surface,
                raised = raised,
                glass = glass,
                text = readable(lerp(Color(0xFFF3F3F5), a, 0.04f), grounds, white, 7.0),
                muted = readable(lerp(Color(0xFFA9A9AF), a, 0.15f), grounds, white, 4.6),
                accent = accent,
                accentFill = fill,
                accentInk = ink,
                secondary = lerp(accent, white, 0.4f),
            )
        }
    }

    /**
     * The accent button: the accent itself with white or black ink, whichever reads, nudged if
     * needed.
     */
    private fun button(a: Color): Pair<Color, Color> {
        val ink = if (a.luminance() > 0.22f) Color.Black else Color.White
        val fill =
            readable(a, listOf(ink), if (ink == Color.White) Color.Black else Color.White, 4.6)
        return fill to ink
    }
}
