package org.nekomanga.shared.extensions

/**
 * JavaScript ports of Mangayomi's Dart multi-site templates (mangayomi-extensions, Apache-2.0). A
 * Dart source built on one of these (most Madara and MangaReader WordPress sites, and the MangaBox
 * family) runs in the same sandbox as a JavaScript source: Beako installs the bundled template
 * instead of the Dart file, and the source's own entry (name, address, date format) from the user's
 * repository fills it in. The templates are generic site layouts, not sources: none is usable until
 * the user adds a repository and installs a source from it.
 */
object Templates {
    private val bundled = setOf("madara", "mangareader", "mangabox")

    /** The template for [typeSource], or null when Beako has no port of it. */
    fun code(typeSource: String): String? {
        val name = typeSource.trim().lowercase()
        if (name !in bundled) return null
        return Templates::class.java.getResourceAsStream("/templates/$name.js")?.use {
            it.readBytes().decodeToString()
        }
    }

    fun has(typeSource: String): Boolean = typeSource.trim().lowercase() in bundled
}

/** Whether Beako can run this source: JavaScript, or Dart built on a template Beako has ported. */
val ExtensionInfo.isRunnable: Boolean
    get() = isJavaScript || Templates.has(typeSource)
