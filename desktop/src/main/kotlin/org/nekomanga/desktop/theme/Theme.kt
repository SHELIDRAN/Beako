package org.nekomanga.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
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

/**
 * A colour scheme. The dark ones change only the ground and surfaces; [light] (Porcelain) also
 * flips text, glass, the droplet and the state colours so they read on a pale ground.
 */
enum class Palette(
    val label: String,
    val ground: Color,
    val groundGlow: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glassSolid: Color,
    val light: Boolean = false,
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
    ),
    PORCELAIN(
        "Porcelain (light)",
        Color(0xFFEBEBEE),
        Color(0xFFF8F8FA),
        Color(0xFFFFFFFF),
        Color(0xFFF3F3F5),
        Color(0xFFF7F7F9),
        light = true,
    );

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: NEUTRAL
    }
}

/**
 * Beako's colours (the "Beako Glass" design system): a ground from the chosen [Palette], white and
 * grey text (near-black on Porcelain), glass, and one orange accent kept for the action that
 * matters most. Backed by Compose state, so changing the palette repaints everything that reads it.
 */
object BeakoColors {
    private var chosen by mutableStateOf(Palette.NEUTRAL)

    /**
     * Set while a screen with its own fixed background (the reader) needs ink that reads on it,
     * whatever palette the user chose; null otherwise.
     */
    var override by mutableStateOf<Palette?>(null)

    /** The user's palette, ignoring any [override]. */
    val chosenPalette: Palette
        get() = chosen

    /** The palette in effect: [override] if set, otherwise the user's choice. */
    var palette: Palette
        get() = override ?: chosen
        set(value) {
            chosen = value
        }

    private val light: Boolean
        get() = palette.light

    private fun pick(dark: Long, porcelain: Long) = Color(if (light) porcelain else dark)

    val Ground: Color
        get() = palette.ground

    val GroundGlow: Color
        get() = palette.groundGlow

    val Surface: Color
        get() = palette.surface

    val SurfaceRaised: Color
        get() = palette.surfaceRaised

    /** Solid stand-in for glass when "reduce transparency" is on. */
    val GlassSolid: Color
        get() = palette.glassSolid

    val Text: Color
        get() = pick(0xFFF5F5F6, 0xFF17171A)

    /** Secondary text; at least 6.8:1 on dark surfaces and 6.1:1 on Porcelain (WCAG AA). */
    val TextMuted: Color
        get() = pick(0xFFA9A9AF, 0xFF55565E)

    val Rim: Color
        get() = pick(0x24FFFFFF, 0x1A000000)

    val RimBright: Color
        get() = pick(0x42FFFFFF, 0xE6FFFFFF)

    /** Tint under text on large glass, light enough to stay see-through. */
    val GlassTint: Color
        get() = pick(0x57090A0C, 0x99FFFFFF)

    val Focus: Color
        get() = Text

    val Gold: Color
        get() = pick(0xFFFFD666, 0xFF8A5C00)

    val Silver: Color
        get() = pick(0xFFE2E4E8, 0xFF6B6E76)

    val Bronze: Color
        get() = pick(0xFFE8A06E, 0xFFA0522D)

    val Positive: Color
        get() = pick(0xFF8EE4AF, 0xFF1A7340)

    val Danger: Color
        get() = pick(0xFFFF8A80, 0xFFC0392B)

    /** Small label chips; [ChipRim] is also the skeleton fill. */
    val ChipFill: Color
        get() = pick(0x24FFFFFF, 0x0D000000)

    val ChipRim: Color
        get() = pick(0x14FFFFFF, 0x14000000)

    /** The wash behind a hovered row. */
    val Hover: Color
        get() = pick(0x12FFFFFF, 0x0A000000)

    val CardHighlight: Color
        get() = pick(0x2EFFFFFF, 0x4DFFFFFF)

    /** Shadow under text on glass captions, so it holds 4.5:1 over bright covers. */
    val TextShadow: Color
        get() = pick(0x8C000000, 0xB3FFFFFF)

    val DropletTop: Color
        get() = pick(0x3DFFFFFF, 0xFFFFFFFF)

    val DropletBottom: Color
        get() = pick(0x14FFFFFF, 0xD9FFFFFF)

    val TriNoneFill: Color
        get() = pick(0x0FFFFFFF, 0x0A000000)

    val TriIncludedFill: Color
        get() = pick(0x33FFFFFF, 0x1417171A)

    val TriIncludedRim: Color
        get() = pick(0x80FFFFFF, 0x8017171A)

    val TriExcludedFill: Color
        get() = pick(0x14FF8A80, 0x14C0392B)

    val TriExcludedRim: Color
        get() = pick(0x99FF8A80, 0x99C0392B)

    val RankPlainFill: Color
        get() = pick(0x660A0A0C, 0xCCFFFFFF)

    val RankRim: Color
        get() = pick(0x47FFFFFF, 0x1F000000)

    val RankGoldFill = Color(0xD9FFD666)
    val RankSilverFill = Color(0xD9E2E4E8)
    val RankBronzeFill = Color(0xD9E8A06E)
    val RankGoldInk = Color(0xFF3A2A00)
    val RankSilverInk = Color(0xFF1C1D20)
    val RankBronzeInk = Color(0xFF3A1A06)

    /** Strong fill that isn't the accent (open groups, toggles that are on). */
    val InkFill: Color
        get() = Text

    val InkOnFill: Color
        get() = pick(0xFF0B0C0E, 0xFFFFFFFF)

    /**
     * The brand orange: icons, the tab underline, unread dots, carets. Never body text (3.2:1 on
     * Porcelain, 5.2:1 on Neutral).
     */
    val Accent = Color(0xFFDF5830)
    /** Fill of accent buttons, so white [AccentInk] holds 4.8:1. */
    val AccentFill = Color(0xFFC8461F)
    val AccentInk = Color(0xFFFFFFFF)

    val AccentSoft: Color
        get() = pick(0x29DF5830, 0x1FDF5830)

    val AccentRim: Color
        get() = pick(0x99DF5830, 0x80DF5830)

    val AccentGlow = Color(0x66DF5830)

    /** Shadow colours: dark drops and, for soft elevation, the light top-left edge. */
    val ShadowDark: Color
        get() = pick(0xB3000000, 0x1F000000)

    val ShadowLight: Color
        get() = pick(0x0AFFFFFF, 0xE6FFFFFF)
}

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Corner radii: controls are full pills, containers 12–18, popovers 20, widgets 28. */
object Radius {
    val chip = 6.dp
    val thumb = 10.dp
    val field = 12.dp
    val caption = 13.dp
    val panel = 16.dp
    val card = 18.dp
    val menu = 20.dp
    val widget = 28.dp
    val sidebar = 30.dp
}

/** Fixed control sizes. */
object Sizes {
    val controlSm = 32.dp
    val controlMd = 36.dp
    val controlLg = 40.dp
    val controlXl = 44.dp
    /** Large calls to action: ArrowButton, ToggleIconButton, SoftField. */
    val controlCta = 48.dp
    val iconSm = 16.dp
    val iconMd = 18.dp
    val iconLg = 22.dp
    val rankChip = 34.dp
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
    if (BeakoColors.palette.light)
        lightColorScheme(
            primary = BeakoColors.Text,
            onPrimary = BeakoColors.Ground,
            secondary = BeakoColors.TextMuted,
            background = BeakoColors.Ground,
            onBackground = BeakoColors.Text,
            surface = BeakoColors.Surface,
            onSurface = BeakoColors.Text,
            surfaceVariant = BeakoColors.SurfaceRaised,
            onSurfaceVariant = BeakoColors.TextMuted,
            outline = BeakoColors.Rim,
            outlineVariant = BeakoColors.Rim,
            error = BeakoColors.Danger,
        )
    else
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
    // Applied before the children compose, so the first frame already uses the chosen palette.
    // BeakoColors' state lives outside composition, so this write is safe here.
    remember(appearance.palette) { BeakoColors.palette = appearance.palette }
    val density = LocalDensity.current
    val scaled =
        remember(density, appearance.scale) {
            Density(density.density * appearance.scale.coerceIn(0.8f, 1.5f), density.fontScale)
        }
    CompositionLocalProvider(LocalAppearance provides appearance, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = beakoScheme(), typography = BeakoTypography, content = content)
    }
}
