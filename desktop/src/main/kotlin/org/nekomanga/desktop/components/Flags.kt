package org.nekomanga.desktop.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Country flags for covers, from the bundled `flag-icons` SVGs (MIT; see
 * `resources/flags/LICENSE-flag-icons.txt`). Windows doesn't draw flag emoji, so these are images.
 */
object Flags {
    private val cache = HashMap<String, Painter?>()

    /** The flag for an ISO country code ("jp", "kr"…), or null when it isn't bundled. */
    @Synchronized
    fun painter(country: String, density: Density): Painter? =
        cache.getOrPut("$country@${density.density}") {
            Flags::class.java.getResourceAsStream("/flags/$country.svg")?.use {
                runCatching { loadSvgPainter(it, density) }.getOrNull()
            }
        }

    /** The country's name in English, for the flag's description. */
    fun name(country: String): String =
        Locale.Builder()
            .setRegion(country.uppercase())
            .build()
            .getDisplayCountry(Locale.ENGLISH)
            .ifBlank { country.uppercase() }
}

/** A small flag badge for [country], with a hairline so pale flags stay visible on light covers. */
@Composable
fun CountryFlag(country: String?, modifier: Modifier = Modifier) {
    val code = country?.trim()?.lowercase()?.takeIf { it.length == 2 } ?: return
    val density = LocalDensity.current
    val painter = remember(code, density) { Flags.painter(code, density) } ?: return
    val shape = RoundedCornerShape(3.dp)
    Image(
        painter,
        contentDescription = null,
        modifier =
            modifier
                .size(width = 22.dp, height = 16.5.dp)
                .shadow(2.dp, shape)
                .clip(shape)
                .border(0.5.dp, Color.Black.copy(alpha = 0.25f), shape)
                .semantics { contentDescription = Flags.name(code) },
    )
}
