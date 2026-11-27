package org.nekomanga.shared.extensions

/**
 * JavaScript ports of Mangayomi's Dart sources (mangayomi-extensions, Apache-2.0). A Dart source
 * built on a ported multi-site template (Madara, MangaReader, MangaBox, MMRCMS, HeanCMS), or one of
 * the ported single-site sources (MangaHere, AllAnime), runs in the same sandbox as a JavaScript
 * source: Beako installs the bundled port instead of the Dart file, and the source's own entry
 * (name, address, date format) from the user's repository fills it in. None is usable until the
 * user adds a repository and installs a source from it.
 */
object Templates {
    /** Multi-site templates, by the index's `typeSource`. */
    private val multiSite = setOf("madara", "mangareader", "mangabox", "mmrcms", "heancms")

    /** Single-site sources (`typeSource` "single"), by name with spaces and case dropped. */
    private val singleSite = setOf("mangahere", "allanime")

    /** The bundled port's name for [info], or null when Beako has none. */
    fun nameFor(info: ExtensionInfo): String? {
        val type = info.typeSource.trim().lowercase()
        if (type in multiSite) return type
        if (type == "single") {
            val name = info.name.lowercase().filter(Char::isLetterOrDigit)
            if (name in singleSite) return name
        }
        return null
    }

    /** The bundled code for [info], or null when Beako has no port of it. */
    fun code(info: ExtensionInfo): String? = nameFor(info)?.let(::code)

    /** The bundled code named [name] ("madara", "mangahere"…), or null. */
    fun code(name: String): String? {
        val key = name.trim().lowercase()
        if (key !in multiSite && key !in singleSite) return null
        return Templates::class.java.getResourceAsStream("/templates/$key.js")?.use {
            it.readBytes().decodeToString()
        }
    }

    fun has(info: ExtensionInfo): Boolean = nameFor(info) != null
}

/** Whether Beako can run this source: JavaScript, or a Dart source Beako has a port of. */
val ExtensionInfo.isRunnable: Boolean
    get() = isJavaScript || Templates.has(this)
