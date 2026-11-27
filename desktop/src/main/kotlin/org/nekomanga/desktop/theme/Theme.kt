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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A colour scheme. Beako's own (Neutral … Porcelain) change only the ground and surfaces and keep
 * neutral text and the orange accent; [light] ones also flip text, glass, the droplet and the state
 * colours so they read on a pale ground. The [fromNeko] ones are Neko's Android themes (Apache-2.0)
 * mapped onto Beako's layers: their own ground, text and accent, adjusted only where contrast fell
 * short (text 7:1, secondary text 4.5:1, accent 3:1, button labels 4.5:1).
 */
enum class Palette(
    val label: String,
    open val ground: Color,
    open val groundGlow: Color,
    open val surface: Color,
    open val surfaceRaised: Color,
    open val glassSolid: Color,
    val light: Boolean = false,
    /** Text and secondary text; null keeps Beako's neutral white/grey (or near-black on light). */
    open val text: Color? = null,
    open val textMuted: Color? = null,
    /** Icons, the tab underline, unread dots: at least 3:1 on [ground]. */
    open val accent: Color = Color(0xFFDF5830),
    /** Accent buttons: [accentInk] on [accentFill] holds at least 4.5:1. */
    open val accentFill: Color = Color(0xFFC8461F),
    open val accentInk: Color = Color(0xFFFFFFFF),
    /** The scheme's second colour, shown in its preview. */
    open val secondary: Color? = null,
    val fromNeko: Boolean = false,
    /** Built from the Windows accent colour at run time ([WindowsAccent]). */
    val dynamic: Boolean = false,
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
    ),
    DYNAMIC_DARK(
        "Dynamic",
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        light = false,
        dynamic = true,
    ) {
        private val scheme
            get() = DynamicScheme.of(WindowsAccent.color, light)

        override val ground
            get() = scheme.ground

        override val groundGlow
            get() = scheme.glow

        override val surface
            get() = scheme.surface

        override val surfaceRaised
            get() = scheme.raised

        override val glassSolid
            get() = scheme.glass

        override val text
            get() = scheme.text

        override val textMuted
            get() = scheme.muted

        override val accent
            get() = scheme.accent

        override val accentFill
            get() = scheme.accentFill

        override val accentInk
            get() = scheme.accentInk

        override val secondary
            get() = scheme.secondary
    },
    KITSUNE_FIRE_DARK(
        "Kitsune Fire",
        Color(0xFF1A1C1E),
        Color(0xFF35372D),
        Color(0xFF212426),
        Color(0xFF2B2F31),
        Color(0xFF262A2C),
        light = false,
        text = Color(0xFFE2E2E6),
        textMuted = Color(0xFFC5C6D1),
        accent = Color(0xFFFCFC98),
        accentFill = Color(0xFFFCFC98),
        accentInk = Color(0xFF313100),
        secondary = Color(0xFF80C4FF),
        fromNeko = true,
    ),
    MIAMI_VICE_DARK(
        "Miami Vice",
        Color(0xFF00101C),
        Color(0xFF0C2837),
        Color(0xFF041724),
        Color(0xFF091D29),
        Color(0xFF061A26),
        light = false,
        text = Color(0xFFE0E6EB),
        textMuted = Color(0xFFC4DCEF),
        accent = Color(0xFF60D6FF),
        accentFill = Color(0xFF60D6FF),
        accentInk = Color(0xFF00415A),
        secondary = Color(0xFFFF95F1),
        fromNeko = true,
    ),
    CHERRY_BLOSSOM_DARK(
        "Cherry Blossom",
        Color(0xFF1D1A1B),
        Color(0xFF382C30),
        Color(0xFF292728),
        Color(0xFF343233),
        Color(0xFF2E2C2E),
        light = false,
        text = Color(0xFFE7E0E1),
        textMuted = Color(0xFFD5C2C7),
        accent = Color(0xFFFFB0CB),
        accentFill = Color(0xFFFFB0CB),
        accentInk = Color(0xFF670034),
        secondary = Color(0xFFCEBCC2),
        fromNeko = true,
    ),
    CRAYON_DARK(
        "Crayon",
        Color(0xFF1A1A1A),
        Color(0xFF292F35),
        Color(0xFF333333),
        Color(0xFF474747),
        Color(0xFF3D3D3D),
        light = false,
        text = Color(0xFFE8E8E8),
        textMuted = Color(0xFFC0C0C0),
        accent = Color(0xFF99CCFF),
        accentFill = Color(0xFF99CCFF),
        accentInk = Color(0xFF003366),
        secondary = Color(0xFF66FFFF),
        fromNeko = true,
    ),
    ROYAL_DARK(
        "Royal",
        Color(0xFF2A1D42),
        Color(0xFF3E3059),
        Color(0xFF32254B),
        Color(0xFF3F325C),
        Color(0xFF382C54),
        light = false,
        text = Color(0xFFE8DDFF),
        textMuted = Color(0xFFCFC3E7),
        accent = Color(0xFFCEBFFF),
        accentFill = Color(0xFFCEBFFF),
        accentInk = Color(0xFF450099),
        secondary = Color(0xFFFFC015),
        fromNeko = true,
    ),
    NEON_NIGHTS_DARK(
        "Neon Nights",
        Color(0xFF1A1C19),
        Color(0xFF1E3326),
        Color(0xFF1E201D),
        Color(0xFF282B27),
        Color(0xFF232622),
        light = false,
        text = Color(0xFFE2E3DD),
        textMuted = Color(0xFFC2C9BE),
        accent = Color(0xFF3DDA83),
        accentFill = Color(0xFF3DDA83),
        accentInk = Color(0xFF003916),
        secondary = Color(0xFFC4BFFF),
        fromNeko = true,
    ),
    NORD_DARK(
        "Nord",
        Color(0xFF2E3440),
        Color(0xFF394551),
        Color(0xFF414C5C),
        Color(0xFF4E5766),
        Color(0xFF485261),
        light = false,
        text = Color(0xFFFAFBFC),
        textMuted = Color(0xFFECEFF4),
        accent = Color(0xFF88C0D0),
        accentFill = Color(0xFF88C0D0),
        accentInk = Color(0xFF2E3440),
        secondary = Color(0xFF81A1C1),
        fromNeko = true,
    ),
    TOKYO_NIGHT_DARK(
        "Tokyo Night",
        Color(0xFF222436),
        Color(0xFF2E344E),
        Color(0xFF2D304B),
        Color(0xFF363B5D),
        Color(0xFF323654),
        light = false,
        text = Color(0xFFC8D3F5),
        textMuted = Color(0xFFA2A9CA),
        accent = Color(0xFF82AAFF),
        accentFill = Color(0xFF82AAFF),
        accentInk = Color(0xFF1B1D2B),
        secondary = Color(0xFF86E1FC),
        fromNeko = true,
    ),
    TAKO_DARK(
        "Tako",
        Color(0xFF21212E),
        Color(0xFF3A3337),
        Color(0xFF2A2A3C),
        Color(0xFF303044),
        Color(0xFF2D2D40),
        light = false,
        text = Color(0xFFE3E0F2),
        textMuted = Color(0xFFCBC4CE),
        accent = Color(0xFFF3B375),
        accentFill = Color(0xFFF3B375),
        accentInk = Color(0xFF38294E),
        secondary = Color(0xFFB6B1D0),
        fromNeko = true,
    ),
    TEAL_OCEAN_DARK(
        "Teal Ocean",
        Color(0xFF191C1B),
        Color(0xFF1F3530),
        Color(0xFF242928),
        Color(0xFF2F3433),
        Color(0xFF2A2E2E),
        light = false,
        text = Color(0xFFE0E3E1),
        textMuted = Color(0xFFBEC9C7),
        accent = Color(0xFF4EEFCE),
        accentFill = Color(0xFF4EEFCE),
        accentInk = Color(0xFF003730),
        secondary = Color(0xFFFFB39D),
        fromNeko = true,
    ),
    JUNGLE_DARK(
        "Jungle",
        Color(0xFF1D1513),
        Color(0xFF2D301E),
        Color(0xFF2B211E),
        Color(0xFF372C28),
        Color(0xFF312623),
        light = false,
        text = Color(0xFFEBE0DD),
        textMuted = Color(0xFFD0C4C1),
        accent = Color(0xFFA6F26D),
        accentFill = Color(0xFFA6F26D),
        accentInk = Color(0xFF1E3700),
        secondary = Color(0xFFBCA784),
        fromNeko = true,
    ),
    CITRUS_SQUEEZE_DARK(
        "Citrus Squeeze",
        Color(0xFF100E0D),
        Color(0xFF2D231B),
        Color(0xFF1C1918),
        Color(0xFF231F1E),
        Color(0xFF201C1B),
        light = false,
        text = Color(0xFFEEE3E1),
        textMuted = Color(0xFFD0C4C1),
        accent = Color(0xFFFFC07F),
        accentFill = Color(0xFFFFC07F),
        accentInk = Color(0xFF752400),
        secondary = Color(0xFFCCFF66),
        fromNeko = true,
    ),
    CHOCOLATE_DARK(
        "Chocolate",
        Color(0xFF2A2220),
        Color(0xFF413432),
        Color(0xFF372D2B),
        Color(0xFF433936),
        Color(0xFF3D3330),
        light = false,
        text = Color(0xFFFBECEB),
        textMuted = Color(0xFFD7C1C1),
        accent = Color(0xFFECB8B4),
        accentFill = Color(0xFFECB8B4),
        accentInk = Color(0xFF3A1A17),
        secondary = Color(0xFFFF8B9C),
        fromNeko = true,
    ),
    DYNAMIC_LIGHT(
        "Dynamic",
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        light = true,
        dynamic = true,
    ) {
        private val scheme
            get() = DynamicScheme.of(WindowsAccent.color, light)

        override val ground
            get() = scheme.ground

        override val groundGlow
            get() = scheme.glow

        override val surface
            get() = scheme.surface

        override val surfaceRaised
            get() = scheme.raised

        override val glassSolid
            get() = scheme.glass

        override val text
            get() = scheme.text

        override val textMuted
            get() = scheme.muted

        override val accent
            get() = scheme.accent

        override val accentFill
            get() = scheme.accentFill

        override val accentInk
            get() = scheme.accentInk

        override val secondary
            get() = scheme.secondary
    },
    KITSUNE_FIRE_LIGHT(
        "Kitsune Fire",
        Color(0xFFF1F1F6),
        Color(0xFFFFF3F6),
        Color(0xFFFFFFFF),
        Color(0xFFF6F6FA),
        Color(0xFFFAFAFC),
        light = true,
        text = Color(0xFF1A1C1E),
        textMuted = Color(0xFF45464F),
        accent = Color(0xFFFF124A),
        accentFill = Color(0xFFE61043),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFF3B4252),
        fromNeko = true,
    ),
    MIAMI_VICE_LIGHT(
        "Miami Vice",
        Color(0xFFE1F0FF),
        Color(0xFFF7FDFF),
        Color(0xFFFFFFFF),
        Color(0xFFECF4FF),
        Color(0xFFF4FAFF),
        light = true,
        text = Color(0xFF00101C),
        textMuted = Color(0xFF3C6380),
        accent = Color(0xFF4192AD),
        accentFill = Color(0xFF60D6FF),
        accentInk = Color(0xFF00415A),
        secondary = Color(0xFFFF00DD),
        fromNeko = true,
    ),
    CHERRY_BLOSSOM_LIGHT(
        "Cherry Blossom",
        Color(0xFFF7EBEB),
        Color(0xFFFFF9FA),
        Color(0xFFFFFFFF),
        Color(0xFFFDF0F3),
        Color(0xFFFCF8F8),
        light = true,
        text = Color(0xFF1D1B1C),
        textMuted = Color(0xFF524347),
        accent = Color(0xFFCC6A81),
        accentFill = Color(0xFFFF85A1),
        accentInk = Color(0xFF000000),
        secondary = Color(0xFF634B52),
        fromNeko = true,
    ),
    CRAYON_LIGHT(
        "Crayon",
        Color(0xFFEDEDE4),
        Color(0xFFF2F7FF),
        Color(0xFFFFFFFF),
        Color(0xFFF7F7F0),
        Color(0xFFF9F9F6),
        light = true,
        text = Color(0xFF1F1E1A),
        textMuted = Color(0xFF55554D),
        accent = Color(0xFF0066FF),
        accentFill = Color(0xFF0066FF),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFF00C7FF),
        fromNeko = true,
    ),
    ROYAL_LIGHT(
        "Royal",
        Color(0xFFF2EBFD),
        Color(0xFFF8F5FA),
        Color(0xFFFFFFFF),
        Color(0xFFF7F1FF),
        Color(0xFFFAF8FE),
        light = true,
        text = Color(0xFF1B1B1F),
        textMuted = Color(0xFF48405B),
        accent = Color(0xFF6A3C9F),
        accentFill = Color(0xFF6A3C9F),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFF987200),
        fromNeko = true,
    ),
    NEON_NIGHTS_LIGHT(
        "Neon Nights",
        Color(0xFFE8E8E8),
        Color(0xFFF9F8F2),
        Color(0xFFFFFFFF),
        Color(0xFFECECEC),
        Color(0xFFF7F7F7),
        light = true,
        text = Color(0xFF1B1B1B),
        textMuted = Color(0xFF424242),
        accent = Color(0xFF8A6C00),
        accentFill = Color(0xFF8A6C00),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFFB00069),
        fromNeko = true,
    ),
    NORD_LIGHT(
        "Nord",
        Color(0xFFDAE0EA),
        Color(0xFFCBD3DD),
        Color(0xFFD1D7E0),
        Color(0xFFD6DCE6),
        Color(0xFFD4DAE4),
        light = true,
        text = Color(0xFF2E3440),
        textMuted = Color(0xFF2E3440),
        accent = Color(0xFF5879A2),
        accentFill = Color(0xFF5E81AC),
        accentInk = Color(0xFF000000),
        secondary = Color(0xFF81A1C1),
        fromNeko = true,
    ),
    TOKYO_NIGHT_LIGHT(
        "Tokyo Night",
        Color(0xFFC1C9DF),
        Color(0xFFDBE0E9),
        Color(0xFFE4E5E9),
        Color(0xFFD0D5E3),
        Color(0xFFD8DBE6),
        light = true,
        text = Color(0xFF1F366B),
        textMuted = Color(0xFF44507B),
        accent = Color(0xFF286ECD),
        accentFill = Color(0xFF2A73D6),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFF007197),
        fromNeko = true,
    ),
    TAKO_LIGHT(
        "Tako",
        Color(0xFFE8E0EB),
        Color(0xFFD1CAD5),
        Color(0xFFD7D0DA),
        Color(0xFFDFD8E2),
        Color(0xFFDDD6E0),
        light = true,
        text = Color(0xFF1B1B22),
        textMuted = Color(0xFF49454E),
        accent = Color(0xFF66577E),
        accentFill = Color(0xFF544767),
        accentInk = Color(0xFFF3B375),
        secondary = Color(0xFF52526B),
        fromNeko = true,
    ),
    TEAL_OCEAN_LIGHT(
        "Teal Ocean",
        Color(0xFFEEF5F6),
        Color(0xFFF2F8F8),
        Color(0xFFFFFFFF),
        Color(0xFFF5FAFB),
        Color(0xFFF9FCFC),
        light = true,
        text = Color(0xFF191C1D),
        textMuted = Color(0xFF3F484A),
        accent = Color(0xFF006B7B),
        accentFill = Color(0xFF006B7B),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFFE47055),
        fromNeko = true,
    ),
    JUNGLE_LIGHT(
        "Jungle",
        Color(0xFFF1F4ED),
        Color(0xFFF5F8F2),
        Color(0xFFFFFFFF),
        Color(0xFFF7F9F1),
        Color(0xFFFAFBF9),
        light = true,
        text = Color(0xFF1A1C18),
        textMuted = Color(0xFF43483E),
        accent = Color(0xFF386A00),
        accentFill = Color(0xFF386A00),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFF745900),
        fromNeko = true,
    ),
    CITRUS_SQUEEZE_LIGHT(
        "Citrus Squeeze",
        Color(0xFFF5ECEA),
        Color(0xFFFFF7F4),
        Color(0xFFFFFFFF),
        Color(0xFFFAF2F0),
        Color(0xFFFCF8F8),
        light = true,
        text = Color(0xFF373130),
        textMuted = Color(0xFF655D5C),
        accent = Color(0xFFE65D19),
        accentFill = Color(0xFFC24E15),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFFA5CC00),
        fromNeko = true,
    ),
    CHOCOLATE_LIGHT(
        "Chocolate",
        Color(0xFFF6EFEF),
        Color(0xFFF7F6F5),
        Color(0xFFFFFFFF),
        Color(0xFFFBF4F3),
        Color(0xFFFCF9F9),
        light = true,
        text = Color(0xFF1D1B1A),
        textMuted = Color(0xFF524442),
        accent = Color(0xFF674840),
        accentFill = Color(0xFF674840),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFFB72D43),
        fromNeko = true,
    ),
    MONOCHROME_LIGHT(
        "Monochrome",
        Color(0xFFFFFFFF),
        Color(0xFFF2F2F2),
        Color(0xFFFFFFFF),
        Color(0xFFFFFFFF),
        Color(0xFFFFFFFF),
        light = true,
        text = Color(0xFF000000),
        textMuted = Color(0xFF000000),
        accent = Color(0xFF000000),
        accentFill = Color(0xFF000000),
        accentInk = Color(0xFFFFFFFF),
        secondary = Color(0xFF000000),
        fromNeko = true,
    );

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: NEUTRAL

        /**
         * The scheme to show: with [follow] on and Windows' mode known, the chosen light or dark
         * scheme for that mode; otherwise the one picked last.
         */
        fun effective(
            palette: String,
            lightPalette: String,
            darkPalette: String,
            follow: Boolean,
            systemLight: Boolean?,
        ): Palette =
            when {
                follow && systemLight == true -> of(lightPalette).takeIf { it.light } ?: PORCELAIN
                follow && systemLight == false -> of(darkPalette).takeIf { !it.light } ?: NEUTRAL
                else -> of(palette)
            }
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
        get() = palette.text ?: pick(0xFFF5F5F6, 0xFF17171A)

    /** Secondary text; at least 6.8:1 on dark surfaces and 6.1:1 on Porcelain (WCAG AA). */
    val TextMuted: Color
        get() = palette.textMuted ?: pick(0xFFA9A9AF, 0xFF55565E)

    val Rim: Color
        get() = pick(0x24FFFFFF, 0x1A000000)

    val RimBright: Color
        get() = pick(0x42FFFFFF, 0xE6FFFFFF)

    /** Tint under text on large glass, light enough to stay see-through. */
    val GlassTint: Color
        get() =
            if (light) palette.surface.copy(alpha = 0.6f)
            else palette.ground.copy(alpha = 0x57 / 255f)

    val Focus: Color
        get() = Text

    val Gold: Color
        get() = readableFor(goldFor, pick(0xFFFFD666, 0xFF8A5C00))

    private val goldFor = java.util.concurrent.ConcurrentHashMap<Palette, Color>()

    val Silver: Color
        get() = pick(0xFFE2E4E8, 0xFF6B6E76)

    val Bronze: Color
        get() = pick(0xFFE8A06E, 0xFFA0522D)

    val Positive: Color
        get() = readableFor(positiveFor, pick(0xFF8EE4AF, 0xFF1A7340))

    private val positiveFor = java.util.concurrent.ConcurrentHashMap<Palette, Color>()

    /** Errors: red, nudged lighter or darker on a scheme where it would fall below 4.5:1. */
    val Danger: Color
        get() =
            dangerFor.getOrPut(palette) {
                readable(
                    pick(0xFFFF8A80, 0xFFC0392B),
                    listOf(
                        palette.ground,
                        palette.surface,
                        palette.surfaceRaised,
                        palette.glassSolid,
                    ),
                    toward = if (light) Color.Black else Color.White,
                )
            }

    private val dangerFor = java.util.concurrent.ConcurrentHashMap<Palette, Color>()

    /** Drops colours worked out from the Dynamic schemes' accent, after it changes. */
    internal fun forgetDerived() {
        listOf(dangerFor, goldFor, positiveFor).forEach { cache ->
            cache.keys.removeAll { it.dynamic }
        }
    }

    /** [base] kept at 4.5:1 on this palette's grounds, worked out once per palette. */
    private fun readableFor(cache: MutableMap<Palette, Color>, base: Color): Color =
        cache.getOrPut(palette) {
            readable(
                base,
                listOf(palette.ground, palette.surface, palette.surfaceRaised, palette.glassSolid),
                toward = if (light) Color.Black else Color.White,
            )
        }

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

    /** The 1 px line nested menu rows hang off. */
    val TreeLine: Color
        get() = pick(0x26FFFFFF, 0x26000000)

    val InkOnFill: Color
        get() = if (light) palette.surface else palette.ground

    /**
     * The brand orange: icons, the tab underline, unread dots, carets. Never body text (3.2:1 on
     * Porcelain, 5.2:1 on Neutral).
     */
    /**
     * A colour taken from the open title's cover ("Theme buttons based on cover"); null uses the
     * scheme's accent. Accents built from it keep the scheme's contrast rules.
     */
    var coverAccent by mutableStateOf<Color?>(null)

    private val coverAccents =
        java.util.concurrent.ConcurrentHashMap<Pair<Color, Palette>, Triple<Color, Color, Color>>()

    /** (accent, fill, ink) from [coverAccent] on this palette. */
    private fun fromCover(color: Color): Triple<Color, Color, Color> =
        coverAccents.getOrPut(color to palette) {
            val accent =
                readable(
                    color,
                    listOf(palette.ground, palette.surface),
                    if (light) Color.Black else Color.White,
                    3.0,
                )
            val (fill, ink) = DynamicScheme.button(color)
            Triple(accent, fill, ink)
        }

    val Accent: Color
        get() = coverAccent?.let { fromCover(it).first } ?: palette.accent

    /** Fill of accent buttons, so white [AccentInk] holds 4.8:1. */
    val AccentFill: Color
        get() = coverAccent?.let { fromCover(it).second } ?: palette.accentFill

    val AccentInk: Color
        get() = coverAccent?.let { fromCover(it).third } ?: palette.accentInk

    val AccentSoft: Color
        get() = Accent.copy(alpha = if (light) 0.12f else 0.16f)

    val AccentRim: Color
        get() = Accent.copy(alpha = if (light) 0.5f else 0.6f)

    val AccentGlow: Color
        get() = Accent.copy(alpha = 0.4f)

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
    /** How much springs bounce and how quickly slides move. */
    val motion: MotionFeel = MotionFeel.BALANCED,
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
        MaterialTheme(colorScheme = beakoScheme(), typography = BeakoTypography) {
            // Right-click menus get Beako's rounded, rimmed style too.
            CompositionLocalProvider(
                androidx.compose.foundation.LocalContextMenuRepresentation provides
                    org.nekomanga.desktop.components.BeakoContextMenu,
                content = content,
            )
        }
    }
}

/** [color] moved toward [toward] just enough to hold [ratio]:1 against every one of [grounds]. */
internal fun readable(
    color: Color,
    grounds: List<Color>,
    toward: Color,
    ratio: Double = 4.5,
): Color {
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }
    var step = 0f
    var candidate = color
    while (step <= 1f && grounds.any { contrast(candidate, it) < ratio }) {
        step += 0.02f
        candidate = androidx.compose.ui.graphics.lerp(color, toward, step.coerceAtMost(1f))
    }
    return candidate
}
