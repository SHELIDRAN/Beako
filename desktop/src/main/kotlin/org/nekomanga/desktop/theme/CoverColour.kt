package org.nekomanga.desktop.theme

import androidx.compose.ui.graphics.Color
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * The colour a cover is "about", for tinting the title page: the most common vivid hue, weighted by
 * how saturated and bright it is. Null for grey or black-and-white covers, or when the cover can't
 * be loaded.
 */
object CoverColour {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Color>()
    private val none =
        java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
        )

    suspend fun of(data: Any, key: String): Color? {
        cache[key]?.let {
            return it
        }
        if (key in none) return null
        val result =
            SingletonImageLoader.get(PlatformContext.INSTANCE)
                .execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(data).build())
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
        val colour = dominant(bitmap.width, bitmap.height) { x, y -> bitmap.getColor(x, y) }
        if (colour == null) none += key else cache[key] = colour
        return colour
    }

    /** The dominant vivid colour of a [w]×[h] image read through [pixel] (ARGB), or null. */
    fun dominant(w: Int, h: Int, pixel: (Int, Int) -> Int): Color? {
        if (w <= 0 || h <= 0) return null
        // 36 hue buckets of 10°, each summing a weight and the colour it saw.
        val weight = DoubleArray(36)
        val r = DoubleArray(36)
        val g = DoubleArray(36)
        val b = DoubleArray(36)
        val step = max(1, min(w, h) / 48)
        for (y in 0 until h step step) for (x in 0 until w step step) {
            val c = pixel(x, y)
            val rr = (c shr 16) and 0xFF
            val gg = (c shr 8) and 0xFF
            val bb = c and 0xFF
            val hi = max(rr, max(gg, bb))
            val lo = min(rr, min(gg, bb))
            if (hi < 40) continue // near black
            val saturation = (hi - lo) / hi.toDouble()
            if (saturation < 0.25) continue // grey
            val hue = hueOf(rr, gg, bb, hi, lo)
            val bucket = (hue / 10).toInt().coerceIn(0, 35)
            val w8 = saturation * saturation * (hi / 255.0)
            weight[bucket] += w8
            r[bucket] += rr * w8
            g[bucket] += gg * w8
            b[bucket] += bb * w8
        }
        val best =
            weight.indices.maxByOrNull {
                weight[it] + 0.6 * (weight[(it + 1) % 36] + weight[(it + 35) % 36])
            } ?: return null
        val total = weight.sum()
        // Too little colour to call it (mostly grey art): leave the scheme alone.
        if (total == 0.0 || weight[best] / total < 0.08) return null
        val wb = weight[best]
        return Color((r[best] / wb).toInt(), (g[best] / wb).toInt(), (b[best] / wb).toInt())
    }

    private fun hueOf(r: Int, g: Int, b: Int, hi: Int, lo: Int): Double {
        val d = (hi - lo).toDouble()
        if (d == 0.0) return 0.0
        val h =
            when (hi) {
                r -> ((g - b) / d) % 6
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            } * 60
        return if (h < 0) h + 360 else h
    }
}
