package org.nekomanga.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Beako's neutral palette: near-black ground, white and grey text, no colour tint. */
object BeakoColors {
    val Ground = Color(0xFF0B0C0E)
    val GroundGlow = Color(0xFF1C1D20)
    val Surface = Color(0xFF15161A)
    val SurfaceRaised = Color(0xFF1D1E23)
    val Text = Color(0xFFF5F5F6)
    /** Secondary text; 7:1 on [Ground]. */
    val TextMuted = Color(0xFFA9A9AF)
    val Rim = Color(0x24FFFFFF)
    val RimBright = Color(0x42FFFFFF)
    /** Dark tint under text on glass, light enough to stay see-through. */
    val GlassTint = Color(0x57090A0C)
    /** Solid stand-in for glass when "reduce transparency" is on. */
    val GlassSolid = Color(0xFF1A1B1F)
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

private val BeakoScheme =
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
)

val LocalAppearance = staticCompositionLocalOf { BeakoAppearance() }

@Composable
fun BeakoTheme(appearance: BeakoAppearance = BeakoAppearance(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(colorScheme = BeakoScheme, typography = BeakoTypography, content = content)
    }
}
