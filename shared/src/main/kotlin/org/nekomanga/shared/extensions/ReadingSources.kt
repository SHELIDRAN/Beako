package org.nekomanga.shared.extensions

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.PageList
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/** A place a title can be read: MangaDex or an installed source, with the title's address there. */
data class SourceMatch(
    val sourceId: String,
    val name: String,
    val url: String,
    val unoriginal: Boolean = false,
)

/**
 * Headers image servers want for pages from extensions (usually a Referer), looked up by URL when
 * the image is loaded or downloaded. Bounded, so long reading sessions don't grow it forever.
 */
class PageHeaders(private val max: Int = 5_000) {
    private val headers = ConcurrentHashMap<String, Map<String, String>>()

    fun remember(url: String, values: Map<String, String>) {
        if (values.isEmpty()) return
        if (headers.size >= max) headers.clear()
        headers[url] = values
    }

    fun forUrl(url: String): Map<String, String> = headers[url].orEmpty()
}

/** Chapters and pages from MangaDex or an installed extension, by the chapter's source. */
class ReadingSources(
    private val mangaDex: MangaDexApi,
    private val extensions: ExtensionManager,
    val pageHeaders: PageHeaders = PageHeaders(),
    private val dataSaver: () -> Boolean = { false },
) {
    /** True when any installed source can be searched. */
    val hasExtensions: Boolean
        get() = extensions.usable.isNotEmpty()

    /** An installed source's name, or null when it's no longer installed and enabled. */
    fun nameOf(sourceId: String): String? =
        extensionId(sourceId)?.let { id ->
            extensions.usable.firstOrNull { it.info.id == id }?.info?.name
        }

    /** The page images of [chapter]. */
    suspend fun pages(chapter: Chapter): PageList {
        val id = extensionId(chapter.source) ?: return mangaDex.pages(chapter.id, dataSaver())
        val images = guarded(id) { extensions.source(id).pageList(chapter.id) }
        images.forEach { pageHeaders.remember(it.url, it.headers) }
        return PageList(images.map { it.url })
    }

    /**
     * All chapters of the title at [url] on extension [sourceId], newest first like MangaDex's
     * list.
     */
    suspend fun chapters(sourceId: String, url: String): List<Chapter> {
        val id = extensionId(sourceId) ?: return emptyList()
        return guarded(id) {
            val source = extensions.source(id)
            source.detail(url).chapters.map { toChapter(sourceId, source.info.lang, it) }
        }
    }

    /** Extension failures as [ApiError]s, so screens handle them like any other source's. */
    private suspend fun <T> guarded(id: Long, block: suspend () -> T): T {
        val name = extensions.installed.firstOrNull { it.info.id == id }?.info?.name ?: "Source"
        return try {
            block()
        } catch (e: ExtensionError) {
            throw ApiError.SourceFailed(name, e.message ?: "$name failed", e)
        } catch (e: ApiError) {
            throw e
        } catch (e: java.io.IOException) {
            throw ApiError.Unreachable(name, e)
        }
    }

    /**
     * Installed sources that carry [title], found by searching each for the title's names and
     * keeping exact name matches. Searches run in parallel, each given [perSourceMillis].
     */
    suspend fun find(title: Title, perSourceMillis: Long = 20_000): List<SourceMatch> {
        val names =
            (listOf(title.title) + title.altTitles)
                .map(::normalise)
                .filter { it.length >= 2 }
                .toSet()
        val queries =
            (listOf(title.title) + title.altTitles).filter { it.isNotBlank() }.distinct().take(2)
        return coroutineScope {
            extensions.usable
                .map { installed ->
                    async {
                        withTimeoutOrNull(perSourceMillis) {
                            runCatching {
                                val source = extensions.source(installed.info.id)
                                queries.firstNotNullOfOrNull { query ->
                                    source.search(query).titles.firstOrNull {
                                        normalise(it.name) in names
                                    }
                                }
                            }
                                .getOrNull()
                                ?.let {
                                    SourceMatch(
                                        sourceIdOf(installed.info.id),
                                        installed.info.name,
                                        it.url,
                                        installed.info.isUnoriginal,
                                    )
                                }
                        }
                    }
                }
                .mapNotNull { it.await() }
        }
            .sortedBy { it.unoriginal }
    }

    companion object {
        const val PREFIX = "ext:"

        fun sourceIdOf(id: Long) = "$PREFIX$id"

        fun extensionId(source: String): Long? =
            source.removePrefix(PREFIX).takeIf { source.startsWith(PREFIX) }?.toLongOrNull()

        fun isExtension(source: String) = source.startsWith(PREFIX)

        fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

        private val NUMBER = Regex("""(?i)(?:ch(?:apter|\.)?\s*)(\d+(?:\.\d+)?)""")
        private val ANY_NUMBER = Regex("""(\d+(?:\.\d+)?)""")
        private val VOLUME = Regex("""(?i)\bvol(?:ume|\.)?\s*(\d+)""")

        /**
         * An extension chapter as a Beako chapter: its URL is its id, its number read from its
         * name.
         */
        fun toChapter(sourceId: String, language: String, chapter: ExtensionChapter): Chapter {
            val withoutVolume = VOLUME.replace(chapter.name, "")
            val number =
                (NUMBER.find(withoutVolume) ?: ANY_NUMBER.find(withoutVolume))
                    ?.groupValues
                    ?.get(1)
                    ?.let { n ->
                        // "12.50" → "12.5", "12.0" → "12"; whole numbers stay as they are.
                        if ('.' in n) n.trimEnd('0').trimEnd('.') else n
                    }
            return Chapter(
                source = sourceId,
                id = chapter.url,
                number = number,
                volume = VOLUME.find(chapter.name)?.groupValues?.get(1),
                title = chapter.name.takeIf { number == null },
                language = language.ifBlank { "en" },
                group = chapter.scanlator,
                publishedAt = Instant.ofEpochMilli(chapter.dateUpload ?: 0).toString(),
            )
        }

        /** Whether [title] lives on MangaDex or an extension. */
        fun isMangaDex(source: String) = source == Sources.MANGADEX
    }
}
