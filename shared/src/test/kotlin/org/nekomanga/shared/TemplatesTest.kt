package org.nekomanga.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.shared.extensions.ExtensionInfo
import org.nekomanga.shared.extensions.ExtensionManager
import org.nekomanga.shared.extensions.ExtensionRequest
import org.nekomanga.shared.extensions.ExtensionResponse
import org.nekomanga.shared.extensions.ExtensionTransport
import org.nekomanga.shared.extensions.JsSource
import org.nekomanga.shared.extensions.Templates
import org.nekomanga.shared.extensions.isRunnable
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

/** The Dart multi-site templates Beako runs as JavaScript, against small copies of their pages. */
class TemplatesTest {

    private fun dart(name: String, type: String, base: String, dateFormat: String = "") =
        ExtensionInfo(
            id = name.hashCode().toLong(),
            name = name,
            lang = "en",
            baseUrl = base,
            sourceCodeLanguage = 0,
            typeSource = type,
            dateFormat = dateFormat,
            dateFormatLocale = "en_us",
        )

    /** Answers each request with the first page whose key the URL contains. */
    private class Pages(private val pages: List<Pair<String, String>>) : ExtensionTransport {
        val requests = mutableListOf<ExtensionRequest>()

        override suspend fun send(request: ExtensionRequest): ExtensionResponse {
            requests += request
            val body = pages.firstOrNull { request.url.contains(it.first) }?.second
            return ExtensionResponse(
                if (body == null) 404 else 200,
                body ?: "",
                mapOf("content-type" to "text/html"),
                request.url,
            )
        }
    }

    @Test
    fun onlyPortedTemplatesAreRunnable() {
        assertTrue(dart("A", "madara", "https://a.test").isRunnable)
        assertTrue(dart("B", "MangaReader", "https://b.test").isRunnable)
        assertTrue(dart("C", "mangabox", "https://c.test").isRunnable)
        assertFalse(dart("D", "nepnep", "https://d.test").isRunnable)
        assertFalse(dart("E", "single", "https://e.test").isRunnable)
        listOf("madara", "mangareader", "mangabox").forEach { assertNotNull(Templates.code(it)) }
        assertNull(Templates.code("../madara"))
    }

    @Test
    fun datesReadMangayomiLocalesAndRelativeTimes() {
        val now = 1_767_312_000_000L
        val parsed =
            org.nekomanga.shared.extensions.HostUtils.parseDates(
                listOf("January 2, 2026", "3 days ago", "an hour ago", "yesterday", "soon"),
                "MMMM d, yyyy",
                "en_us",
                now,
            )
        assertTrue(parsed[0].isNotEmpty())
        assertEquals((now - 3 * 86_400_000L).toString(), parsed[1])
        assertEquals((now - 3_600_000L).toString(), parsed[2])
        assertEquals((now - 86_400_000L).toString(), parsed[3])
        assertEquals("", parsed[4])
    }

    @Test
    fun madaraListsSearchesReadsChaptersFromAjaxAndPages() {
        runBlocking {
            val site =
                Pages(
                    listOf(
                        "/manga/page/1/?m_orderby=views" to
                            """<div class="page-item-detail"><img data-src="https://cdn.m.test/1.jpg">
                               <div class="post-title"><h3><a href="https://m.test/manga/tower/">Tower</a></h3></div></div>""",
                        "?s=tower" to
                            """<div class="c-tabs-item__content"><img src="https://cdn.m.test/1.jpg">
                               <div class="post-title"><a href="https://m.test/manga/tower/">Tower</a></div></div>""",
                        "admin-ajax.php" to "",
                        "/manga/tower/ajax/chapters/" to
                            """<ul><li class="wp-manga-chapter"><a href="https://m.test/manga/tower/ch-2/">Chapter 2</a>
                               <span class="chapter-release-date"><i>January 2, 2026</i></span></li>
                               <li class="wp-manga-chapter"><a href="https://m.test/manga/tower/ch-1/">Chapter 1</a>
                               <span class="chapter-release-date"><i>coming soon</i></span></li></ul>""",
                        "/manga/tower/ch-2/" to
                            """<div class="page-break"><img data-src=" https://cdn.m.test/p1.jpg "></div>
                               <div class="page-break"><img src="https://cdn.m.test/p2.jpg"></div>""",
                        "/manga/tower/" to
                            """<div class="summary_image"><img src="https://cdn.m.test/cover.jpg"></div>
                               <div class="author-content"><a>Hana</a></div>
                               <div class="description-summary"><div class="summary__content"><p>A tall tower.</p></div></div>
                               <div class="genres-content"><a>Action</a><a>Drama</a></div>
                               <div id="manga-chapters-holder" data-id="42"></div>""",
                    ),
                )
            val info = dart("Madara Site", "madara", "https://m.test/", "MMMM dd, yyyy")
            JsSource(info, Templates.code("madara")!!, site).use { source ->
                val popular = source.popular(1)
                assertEquals(listOf("Tower"), popular.titles.map { it.name })
                assertEquals("https://cdn.m.test/1.jpg", popular.titles[0].imageUrl)
                assertEquals("https://m.test/", site.requests.last().headers["Referer"])

                source.search("tower")
                assertTrue(site.requests.last().url.endsWith("/?s=tower&post_type=wp-manga"))

                val detail = source.detail("https://m.test/manga/tower/")
                assertEquals("Hana", detail.author)
                assertEquals("A tall tower.", detail.description)
                assertEquals(listOf("Action", "Drama"), detail.genres)
                assertEquals(listOf("Chapter 2", "Chapter 1"), detail.chapters.map { it.name })
                assertNotNull(detail.chapters[0].dateUpload)
                // An unreadable date stays unknown.
                assertNull(detail.chapters[1].dateUpload)
                val ajax = site.requests.first { it.url.endsWith("admin-ajax.php") }
                assertEquals("POST", ajax.method)

                val pages = source.pageList("https://m.test/manga/tower/ch-2/")
                assertEquals(
                    listOf("https://cdn.m.test/p1.jpg", "https://cdn.m.test/p2.jpg"),
                    pages.map { it.url },
                )
            }
        }
    }

    @Test
    fun mangaReaderListsReadsChaptersAndPagesFromItsScript() {
        runBlocking {
            val site =
                Pages(
                    listOf(
                        "/manga/?page=1&order=update" to
                            """<div class="listupd"><div class="bs"><div class="bsx">
                               <a href="https://r.test/manga/blade/" title="Blade"><img src="https://cdn.r.test/b.jpg"></a>
                               </div></div></div>""",
                        "/manga/blade/" to
                            """<div class="bigcontent"><div class="thumb"><img src="https://cdn.r.test/b.jpg"></div>
                               <div class="tsinfo"><div class="imptdt">Author <i>Kai</i></div></div>
                               <div class="desc">Sharp.</div><div class="mgen"><a>Action</a></div></div>
                               <div id="chapterlist"><ul><li><div class="eph-num"><a href="https://r.test/blade-1/">
                               <span class="chapternum">Chapter 1</span><span class="chapterdate">March 5, 2026</span></a></div></li></ul></div>""",
                        "/blade-1/" to
                            """<div id="readerarea"></div><script>ts_reader.run({"images":["https://cdn.r.test/1.jpg","https://cdn.r.test/2.jpg"]});</script>""",
                    ),
                )
            val info = dart("Reader Site", "mangareader", "https://r.test", "MMMM d, yyyy")
            JsSource(info, Templates.code("mangareader")!!, site).use { source ->
                val latest = source.latest(1)
                assertEquals(listOf("Blade"), latest.titles.map { it.name })
                assertEquals("https://cdn.r.test/b.jpg", latest.titles[0].imageUrl)

                val detail = source.detail("https://r.test/manga/blade/")
                assertEquals("Kai", detail.author)
                assertEquals("Sharp.", detail.description)
                assertEquals(listOf("Action"), detail.genres)
                assertEquals(listOf("Chapter 1"), detail.chapters.map { it.name })
                assertNotNull(detail.chapters[0].dateUpload)

                val pages = source.pageList("https://r.test/blade-1/")
                assertEquals(2, pages.size)
            }
        }
    }

    @Test
    fun mangaBoxListsSearchesWithItsQueryFormAndReadsPages() {
        runBlocking {
            val site =
                Pages(
                    listOf(
                        "manga-list/hot-manga?page=1" to
                            """<div class="content-genres-item"><a href="https://x.test/manga/one"><img src="https://cdn.x.test/1.jpg"></a>
                               <h3><a href="https://x.test/manga/one">One Piece</a></h3></div>""",
                        "search/story/" to
                            """<div class="search-story-item"><a href="https://x.test/manga/one" title="One Piece"><img src="https://cdn.x.test/1.jpg"></a></div>""",
                        "/manga/one/chapter-1" to
                            """<div class="container-chapter-reader"><img src="https://cdn.x.test/p1.jpg"><img src="https://cdn.x.test/p2.jpg"></div>""",
                        "/manga/one" to
                            """<table><tr><td class="table-label">Author(s) :</td><td><a>Oda</a></td></tr>
                               <tr><td class="table-label">Genres :</td><td><a>Action</a><a>Adventure</a></td></tr></table>
                               <div id="contentBox">Description : Pirates.</div>
                               <ul class="row-content-chapter"><li><a href="https://x.test/manga/one/chapter-1">Chapter 1</a><span>Jan 02,2026</span></li></ul>""",
                    ),
                )
            val info = dart("Manganato", "mangabox", "https://x.test")
            JsSource(info, Templates.code("mangabox")!!, site).use { source ->
                val popular = source.popular(1)
                assertEquals(listOf("One Piece"), popular.titles.map { it.name })
                assertEquals("https://cdn.x.test/1.jpg", popular.titles[0].imageUrl)

                val found = source.search("One Piece: Film!")
                assertTrue(site.requests.last().url.endsWith("search/story/one_piece_film?page=1"))
                assertEquals("https://x.test/manga/one", found.titles.single().url)

                val detail = source.detail("https://x.test/manga/one")
                assertEquals(listOf("Action", "Adventure"), detail.genres)
                assertEquals(listOf("Chapter 1"), detail.chapters.map { it.name })
                // No date format in the index: the date stays unknown.
                assertNull(detail.chapters[0].dateUpload)

                assertEquals(2, source.pageList("https://x.test/manga/one/chapter-1").size)
            }
        }
    }

    @Test
    fun installingATemplateSourceDownloadsNothing() {
        runBlocking {
            val folder = Files.createTempDirectory("beako-ext").toFile()
            try {
                var downloads = 0
                val http = HttpFetcher { _ ->
                    downloads++
                    HttpResponse(500, null, "")
                }
                val manager = ExtensionManager(folder, http, Pages(emptyList()))
                val madara = dart("Madara Site", "madara", "https://m.test")
                manager.install(madara, "https://repo.test/index.json")
                assertEquals(0, downloads)
                assertEquals(listOf(madara.id), manager.usable.map { it.info.id })
                assertTrue(File(folder, "${madara.id}.js").readText().contains("wp-manga"))

                assertFailsWith<java.io.IOException> {
                    manager.install(dart("See", "nepnep", "https://s.test"), "https://repo.test")
                }
            } finally {
                folder.deleteRecursively()
            }
        }
    }
}
