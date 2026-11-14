package org.nekomanga.shared.extensions

import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import org.nekomanga.shared.net.BeakoJson

/** Reading Mangayomi repository indexes and the links people paste. */
object Repositories {

    /**
     * The manga index URL in what the user pasted: a plain `https://…/index.json`, or a
     * `mangayomi://add-repo?…&manga_url=…` link from wotaku.wiki (anime and novel indexes are
     * ignored). Null when there's no usable https URL.
     */
    fun indexUrl(pasted: String): String? {
        val text = pasted.trim()
        val candidate =
            if (text.startsWith("mangayomi://", ignoreCase = true)) {
                val query = text.substringAfter('?', "")
                query
                    .split('&')
                    .map { it.split('=', limit = 2) }
                    .firstOrNull { it.size == 2 && it[0] == "manga_url" }
                    ?.let { URLDecoder.decode(it[1], Charsets.UTF_8) }
            } else text
        val uri = runCatching { URI(candidate ?: return null) }.getOrNull() ?: return null
        return candidate.takeIf {
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }
    }

    /** The sources an index lists, manga only. */
    fun parseIndex(json: String): List<ExtensionInfo> =
        try {
            BeakoJson.decodeFromString(ListSerializer(ExtensionInfo.serializer()), json).filter {
                it.itemType == 0
            }
        } catch (e: SerializationException) {
            throw java.io.IOException("This isn’t a Mangayomi repository index.", e)
        } catch (e: IllegalArgumentException) {
            throw java.io.IOException("This isn’t a Mangayomi repository index.", e)
        }

    /** True when [candidate] is newer than [installed] ("1.0.10" > "1.0.9"). */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
