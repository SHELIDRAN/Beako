package org.nekomanga.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A colour scheme: the ground and surfaces change, text and glass stay neutral. */
enum class Palette(
    val label: String,
    val ground: Color,
    val groundGlow: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glassSolid: Color,
) {
    NEUTRAL(
        "Neutral",
        Color(0xFF0B0C0E),
        Color(0xFF1C1D20),
        Color(0xFF15161A),
        Color(0xFF1D1E23),
        Color(0xFF1A1B1F),
    ),
    MIDNIGHT(
        "Midnight",
        Color(0xFF090B10),
        Color(0xFF172033),
        Color(0xFF121722),
        Color(0xFF1A2130),
        Color(0xFF161C29),
    ),
    WARM(
        "Warm",
        Color(0xFF0E0C0A),
        Color(0xFF221C17),
        Color(0xFF181512),
        Color(0xFF211D19),
        Color(0xFF1D1915),
    ),
    FOREST(
        "Forest",
        Color(0xFF090D0B),
        Color(0xFF16231C),
        Color(0xFF121A16),
        Color(0xFF19231E),
        Color(0xFF151E19),
    ),
    OLED(
        "OLED black",
        Color(0xFF000000),
        Color(0xFF111113),
        Color(0xFF0C0C0E),
        Color(0xFF141417),
        Color(0xFF101012),
    );

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: NEUTRAL
    }
}

/**
 * Beako's palette: a dark ground from the chosen [Palette], white and grey text, no colour tint.
 * Backed by Compose state, so changing the palette repaints everything that reads it.
 */
object BeakoColors {
    var palette by mutableStateOf(Palette.NEUTRAL)

    val Ground: Color
        get() = palette.ground

    val GroundGlow: Color
        get() = palette.groundGlow

    val Surface: Color
        get() = palette.surface

    val SurfaceRaised: Color
        get() = palette.surfaceRaised

    val Text = Color(0xFFF5F5F6)
    /** Secondary text; at least 6.8:1 on every palette's surfaces (WCAG AA). */
    val TextMuted = Color(0xFFA9A9AF)
    val Rim = Color(0x24FFFFFF)
    val RimBright = Color(0x42FFFFFF)
    /** Dark tint under text on glass, light enough to stay see-through. */
    val GlassTint = Color(0x57090A0C)
    /** Solid stand-in for glass when "reduce transparency" is on. */
    val GlassSolid: Color
        get() = palette.glassSolid

    val Gold = Color(0xFFFFD666)
    val Silver = Color(0xFFE2E4E8)
    val Bronze = Color(0xFFE8A06E)
    val Positive = Color(0xFF8EE4AF)
    val Danger = Color(0xFFFF8A80)
    val Focus = Color(0xFFFFFFFF)
}

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

val Inter =
    FontFamily(
        Font(resource = "fonts/Inter-Regular.otf", weight = FontWeight.Normal),
        Font(resource = "fonts/Inter-Medium.otf", weight = FontWeight.Medium),
        Font(resource = "fonts/Inter-SemiBold.otf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/Inter-Bold.otf", weight = FontWeight.Bold),
    )

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = Inter,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

private val BeakoTypography =
    Typography(
        displaySmall = style(32, FontWeight.Bold, 38, -0.6),
        headlineMedium = style(26, FontWeight.Bold, 32, -0.5),
        headlineSmall = style(22, FontWeight.Bold, 28, -0.3),
        titleLarge = style(18, FontWeight.SemiBold, 24, -0.2),
        titleMedium = style(15, FontWeight.SemiBold, 20),
        titleSmall = style(13, FontWeight.SemiBold, 18),
        bodyLarge = style(15, FontWeight.Normal, 22),
        bodyMedium = style(14, FontWeight.Normal, 20),
        bodySmall = style(12, FontWeight.Normal, 17),
        labelLarge = style(14, FontWeight.SemiBold, 18),
        labelMedium = style(12, FontWeight.SemiBold, 16),
        labelSmall = style(11, FontWeight.Medium, 14),
    )

private fun beakoScheme() =
    darkColorScheme(
        primary = BeakoColors.Text,
        onPrimary = BeakoColors.Ground,
        secondary = BeakoColors.TextMuted,
        background = BeakoColors.Ground,
        onBackground = BeakoColors.Text,
        surface = BeakoColors.Surface,
        onSurface = BeakoColors.Text,
        surfaceVariant = BeakoColors.SurfaceRaised,
        onSurfaceVariant = BeakoColors.TextMuted,
        outline = BeakoColors.RimBright,
        outlineVariant = BeakoColors.Rim,
        error = BeakoColors.Danger,
    )

enum class SidebarMode {
    AUTO_COLLAPSE,
    ALWAYS_EXPANDED,
    ICONS_ONLY,
}

/** Look-and-feel settings every component reads. */
@Immutable
data class BeakoAppearance(
    /** Makes every transition instant. */
    val reduceMotion: Boolean = false,
    /** Replaces glass with solid panels. */
    val reduceTransparency: Boolean = false,
    val sidebarMode: SidebarMode = SidebarMode.AUTO_COLLAPSE,
    val palette: Palette = Palette.NEUTRAL,
    /** Glass blur and tint strength, 0.5–1.5. */
    val glassStrength: Float = 1f,
    /** Interface size, 0.8–1.5 (on top of Windows display scaling). */
    val scale: Float = 1f,
)

val LocalAppearance = staticCompositionLocalOf { BeakoAppearance() }

@Composable
fun BeakoTheme(appearance: BeakoAppearance = BeakoAppearance(), content: @Composable () -> Unit) {
    SideEffect { BeakoColors.palette = appearance.palette }
    val density = LocalDensity.current
    val scaled =
        remember(density, appearance.scale) {
            Density(density.density * appearance.scale.coerceIn(0.8f, 1.5f), density.fontScale)
        }
    CompositionLocalProvider(LocalAppearance provides appearance, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = beakoScheme(), typography = BeakoTypography, content = content)
    }
}
