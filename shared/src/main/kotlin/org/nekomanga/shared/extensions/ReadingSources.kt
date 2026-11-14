package org.nekomanga.shared.extensions

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.nekomanga.shared.diagnostics.DiagnosticLog
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.PageList
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/** One installed source's answer for a title: a match, nothing, or an [error] message. */
data class SourceCheck(
    val sourceId: String,
    val name: String,
    val match: SourceMatch?,
    val error: String?,
)

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

    /**
     * Records a page URL an extension gave, with its headers; such URLs get the sandbox's rules.
     */
    fun remember(url: String, values: Map<String, String>) {
        if (headers.size >= max) headers.clear()
        headers[url] = values
    }

    /** True when [url] came from an extension, so loading it must follow [HostPolicy]. */
    fun isFromExtension(url: String): Boolean = headers.containsKey(url)

    fun forUrl(url: String): Map<String, String> = headers[url].orEmpty()
}

/**
 * Per-title search results on each installed source, so reopening a title page doesn't search every
 * source again. A match is kept for the session; "not found" for [missTtlMillis], since sources add
 * titles. Bounded to [max] titles.
 */
internal class SearchCache(
    private val missTtlMillis: Long = 6 * 60 * 60 * 1000L,
    private val max: Int = 2_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    class Entry(val match: SourceMatch?, val at: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(titleKey: String, sourceId: String): Entry? {
        val entry = entries["$titleKey|$sourceId"] ?: return null
        return entry.takeIf { it.match != null || now() - it.at < missTtlMillis }
    }

    fun put(titleKey: String, sourceId: String, match: SourceMatch?) {
        if (entries.size >= max) entries.clear()
        entries["$titleKey|$sourceId"] = Entry(match, now())
    }
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

    /** Installed, enabled sources as (source id, name), for the source picker. */
    val usableSources: List<Pair<String, String>>
        get() = extensions.usable.map { sourceIdOf(it.info.id) to it.info.name }

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
            DiagnosticLog.error("Source $name (id $id) failed", e)
            throw ApiError.SourceFailed(name, e.message ?: "$name failed", e)
        } catch (e: ApiError) {
            DiagnosticLog.error("Source $name (id $id) failed", e)
            throw e
        } catch (e: java.io.IOException) {
            DiagnosticLog.error("Source $name (id $id) couldn’t be reached", e)
            throw ApiError.Unreachable(name, e)
        }
    }

    /**
     * Searches one installed source by hand, for the source picker: [query] can be any name
     * (romaji, the original title…) when the automatic match missed.
     */
    suspend fun search(sourceId: String, query: String): List<ExtensionManga> {
        val id = extensionId(sourceId) ?: return emptyList()
        return guarded(id) { extensions.source(id).search(query).titles }
    }

    /** Remembers a title the user picked by hand on [match]'s source. */
    fun bind(title: Title, match: SourceMatch) {
        searches.put("${title.source}:${title.id}", match.sourceId, match)
    }

    /**
     * Installed sources that carry [title], found by searching each for the title's names and
     * keeping exact name matches. Searches run in parallel, each given [perSourceMillis].
     */
    suspend fun find(
        title: Title,
        skip: Set<String> = emptySet(),
        perSourceMillis: Long = 20_000,
    ): List<SourceMatch> = check(title, skip, perSourceMillis).mapNotNull { it.match }

    /**
     * Every installed source's answer for [title]: a match, no match, or an error. Errors are
     * logged and reported, not hidden, so the source picker can show them.
     */
    suspend fun check(
        title: Title,
        skip: Set<String> = emptySet(),
        perSourceMillis: Long = 20_000,
    ): List<SourceCheck> {
        val names =
            (listOf(title.title) + title.altTitles)
                .map(::normalise)
                .filter { it.length >= 2 }
                .toSet()
        val queries =
            (listOf(title.title) + title.altTitles).filter { it.isNotBlank() }.distinct().take(2)
        val titleKey = "${title.source}:${title.id}"
        val results = coroutineScope {
            extensions.usable
                .filter { sourceIdOf(it.info.id) !in skip }
                .map { installed ->
                    async {
                        val sourceId = sourceIdOf(installed.info.id)
                        val name = installed.info.name
                        // Sources already searched for this title answer from the cache.
                        searches.get(titleKey, sourceId)?.let {
                            return@async SourceCheck(sourceId, name, it.match, null)
                        }
                        val outcome =
                            withTimeoutOrNull(perSourceMillis) {
                                runCatching {
                                    val source = extensions.source(installed.info.id)
                                    queries.firstNotNullOfOrNull { query ->
                                        source.search(query).titles.firstOrNull {
                                            normalise(it.name) in names
                                        }
                                    }
                                }
                            }
                        when {
                            outcome == null -> {
                                DiagnosticLog.error(
                                    "Source $name (id ${installed.info.id}) timed out searching for “${title.title}”",
                                )
                                SourceCheck(sourceId, name, null, "took too long to answer")
                            }
                            outcome.isFailure -> {
                                val error = outcome.exceptionOrNull()
                                DiagnosticLog.error(
                                    "Source $name (id ${installed.info.id}) failed searching for “${title.title}”",
                                    error,
                                )
                                // Only definite answers are cached: failures are tried again.
                                SourceCheck(
                                    sourceId,
                                    name,
                                    null,
                                    error?.message ?: error?.let { it::class.simpleName },
                                )
                            }
                            else -> {
                                val match =
                                    outcome.getOrNull()?.let {
                                        SourceMatch(
                                            sourceId,
                                            name,
                                            it.url,
                                            installed.info.isUnoriginal,
                                        )
                                    }
                                searches.put(titleKey, sourceId, match)
                                SourceCheck(sourceId, name, match, null)
                            }
                        }
                    }
                }
                .map { it.await() }
        }
        return results.sortedBy { it.match?.unoriginal ?: false }
    }

    private val searches = SearchCache()

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
                // No date rather than 1970 when the source doesn't give one.
                publishedAt =
                    chapter.dateUpload?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
            )
        }

        /** Whether [title] lives on MangaDex or an extension. */
        fun isMangaDex(source: String) = source == Sources.MANGADEX
    }
}
