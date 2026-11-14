package org.nekomanga.shared

import java.io.IOException
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.nekomanga.shared.extensions.ExtensionError
import org.nekomanga.shared.extensions.ExtensionInfo
import org.nekomanga.shared.extensions.ExtensionRequest
import org.nekomanga.shared.extensions.ExtensionResponse
import org.nekomanga.shared.extensions.ExtensionTransport
import org.nekomanga.shared.extensions.HostPolicy
import org.nekomanga.shared.extensions.JsSource
import org.nekomanga.shared.extensions.Repositories

class ExtensionTest {

    private val info =
        ExtensionInfo(
            id = 1,
            name = "Sample",
            lang = "en",
            baseUrl = "https://sample.test",
            sourceCodeLanguage = 1,
        )
    private val code = fixture("extensions/sample.js")

    /** Serves the sample site's pages. */
    private class Site : ExtensionTransport {
        val requests = mutableListOf<ExtensionRequest>()

        override suspend fun send(request: ExtensionRequest): ExtensionResponse {
            requests += request
            val html =
                when {
                    request.url.contains("/popular") || request.url.contains("/search") ->
                        """<div class="item"><a href="/title/1"><img src="https://img.sample.test/1.jpg"><h3> Solo Tower </h3></a></div>
                           <div class="item"><a href="/title/2"><img src="https://img.sample.test/2.jpg"><h3>Second</h3></a></div>"""
                    request.url.endsWith("/title/1") ->
                        """<html><head><meta name="description" content="A tower."></head><body><h1>Solo Tower</h1>
                           <span class="genre">Action</span><span class="genre">Fantasy</span>
                           <ul><li class="chapter"><a href="/c/2" data-group="Team A">Chapter 2</a></li>
                           <li class="chapter"><a href="/c/1">Chapter 1</a></li></ul></body></html>"""
                    request.url.endsWith("/c/2") ->
                        """<img class="page" src="https://img.sample.test/p1.jpg"><img class="page" src="https://img.sample.test/p2.jpg">"""
                    else -> "not found"
                }
            return ExtensionResponse(200, html, mapOf("content-type" to "text/html"), request.url)
        }
    }

    @Test
    fun javascriptSourcesListSearchDetailAndPages() = runBlocking {
        val site = Site()
        JsSource(info, code, site).use { source ->
            val popular = source.popular(1)
            assertEquals(listOf("Solo Tower", "Second"), popular.titles.map { it.name })
            assertEquals("/title/1", popular.titles[0].url)
            assertEquals("https://img.sample.test/1.jpg", popular.titles[0].imageUrl)
            assertFalse(popular.hasNextPage)
            assertEquals("https://sample.test/", site.requests.last().headers["Referer"])

            // The script reads its own setting's default and gets its filter list.
            source.search("solo tower")
            assertTrue(site.requests.last().url.endsWith("search?q=solo%20tower&order=views&n=1"))

            val detail = source.detail("/title/1")
            assertEquals("Solo Tower", detail.name)
            assertEquals("A tower.", detail.description)
            assertEquals(listOf("Action", "Fantasy"), detail.genres)
            assertEquals(listOf("Chapter 2", "Chapter 1"), detail.chapters.map { it.name })
            assertEquals("Team A", detail.chapters[0].scanlator)
            assertNull(detail.chapters[1].scanlator)
            assertEquals(1767312000000, detail.chapters[0].dateUpload)

            val pages = source.pageList("/c/2")
            assertEquals(3, pages.size)
            assertEquals("https://sample.test/", pages[0].headers["Referer"])
            assertEquals(mapOf("X-Test" to "1"), pages[2].headers)
        }
    }

    @Test
    fun userSettingsOverrideScriptDefaults() = runBlocking {
        val site = Site()
        JsSource(info, code, site, preferences = { mapOf("pref_order" to "az") }).use { source ->
            source.search("x")
            assertTrue(site.requests.last().url.contains("order=az"))
        }
    }

    @Test
    fun brokenScriptsFailWithTheSourceName() =
        runBlocking<Unit> {
            val error =
                assertFailsWith<ExtensionError> {
                    JsSource(
                            info,
                            "class DefaultExtension extends MProvider { async getPopular() { throw new Error('site changed'); } }",
                            Site(),
                        )
                        .use { it.popular(1) }
                }
            assertTrue(error.message!!.startsWith("Sample:"))
            assertTrue(error.message!!.contains("site changed"))
            assertFailsWith<ExtensionError> {
                JsSource(info, "this is not javascript (", Site()).use { it.popular(1) }
            }
        }

    @Test
    fun scriptsCannotReachThisComputerTheLocalNetworkOrComick() {
        for (url in
            listOf(
                "http://127.0.0.1:47823/comick/callback",
                "http://localhost/x",
                "http://192.168.1.1/",
                "http://10.0.0.2/",
                "http://[::1]/",
                "file:///C:/Windows/win.ini",
                "https://api.comick.dev/v1.0/search",
                "https://comick.io/comic/x",
            )) {
            assertFailsWith<HostPolicy.Blocked>(url) { HostPolicy.check(url) }
        }
        HostPolicy.check("https://mangapill.com/search?q=a")
        assertTrue(HostPolicy.isPrivate(InetAddress.getByName("100.64.1.1")))
        assertFalse(HostPolicy.isPrivate(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun repositoryLinksAndIndexes() {
        assertEquals(
            "https://raw.githubusercontent.com/x/y/main/index.json",
            Repositories.indexUrl(
                "mangayomi://add-repo?repo_name=X&anime_url=https%3A%2F%2Fa.test%2Fanime.json&manga_url=https%3A%2F%2Fraw.githubusercontent.com%2Fx%2Fy%2Fmain%2Findex.json",
            ),
        )
        assertEquals(
            "https://k.github.io/index.json",
            Repositories.indexUrl(" https://k.github.io/index.json "),
        )
        assertNull(Repositories.indexUrl("http://insecure.test/index.json"))
        assertNull(Repositories.indexUrl("mangayomi://add-repo?anime_url=https%3A%2F%2Fa.test"))

        val index =
            Repositories.parseIndex(
                """[{"name":"A","id":5,"lang":"en","sourceCodeLanguage":1,"itemType":0,"extra":1},
                   {"name":"Anime","id":6,"itemType":1}]""",
            )
        assertEquals(listOf("A"), index.map { it.name })
        assertTrue(index[0].isJavaScript)
        assertFailsWith<IOException> { Repositories.parseIndex("<html>") }
        assertTrue(Repositories.isNewer("1.0.10", "1.0.9"))
        assertFalse(Repositories.isNewer("1.0.4", "1.0.4"))
    }

    @Test
    fun installedSourcesFindTitlesAndServeChaptersAndPages() =
        runBlocking<Unit> {
            val dir = java.nio.file.Files.createTempDirectory("beako-ext").toFile()
            val index =
                """[{"name":"Sample","id":1,"lang":"en","baseUrl":"https://sample.test","version":"1.0.0",
                "sourceCodeUrl":"https://repo.test/sample.js","sourceCodeLanguage":1,"itemType":0},
               {"name":"Dart one","id":2,"sourceCodeUrl":"https://repo.test/d.dart","sourceCodeLanguage":0}]"""
            val http = FakeHttp { url ->
                if (url.endsWith("index.json")) json(index) else json(code)
            }
            val manager = org.nekomanga.shared.extensions.ExtensionManager(dir, http, Site())
            val listed = manager.addRepository("https://repo.test/index.json")
            assertEquals(listOf("Sample", "Dart one"), listed.map { it.name })
            assertFailsWith<IOException> {
                manager.install(listed[1], "https://repo.test/index.json")
            }
            manager.install(listed[0], "https://repo.test/index.json")
            assertEquals(listOf(1L), manager.usable.map { it.info.id })

            val sources =
                org.nekomanga.shared.extensions.ReadingSources(
                    org.nekomanga.shared.mangadex.MangaDexApi(http),
                    manager,
                )
            val title =
                org.nekomanga.shared.model.Title(
                    org.nekomanga.shared.model.Sources.COMICK,
                    "hid",
                    "Solo Tower",
                )
            val match = sources.find(title).single()
            assertEquals("ext:1", match.sourceId)
            assertEquals("/title/1", match.url)

            val chapters = sources.chapters(match.sourceId, match.url)
            assertEquals(listOf("2", "1"), chapters.map { it.number })
            assertEquals("ext:1", chapters[0].source)
            assertEquals("/c/2", chapters[0].id)

            val pages = sources.pages(chapters[0])
            assertEquals("https://img.sample.test/p1.jpg", pages.urls[0])
            assertEquals(
                "https://sample.test/",
                sources.pageHeaders.forUrl(pages.urls[0])["Referer"],
            )

            // Settings survive a restart; removing deletes the code.
            val reopened = org.nekomanga.shared.extensions.ExtensionManager(dir, http, Site())
            assertEquals(listOf("https://repo.test/index.json"), reopened.data.value.repositories)
            reopened.uninstall(1)
            assertTrue(reopened.installed.isEmpty())
            assertFalse(java.io.File(dir, "1.js").exists())
            manager.closeAll()
        }

    @Test
    fun chapterNumbersAreReadFromNames() {
        fun number(name: String) =
            org.nekomanga.shared.extensions.ReadingSources.toChapter(
                    "ext:1",
                    "en",
                    org.nekomanga.shared.extensions.ExtensionChapter(name, "/u", null, null),
                )
                .number
        assertEquals("10", number("Chapter 10"))
        assertEquals("12.5", number("Vol. 3 Ch. 12.50"))
        assertEquals("120", number("Solo Tower 120"))
        assertEquals("7", number("Ch.7 - The Gate"))
        assertNull(number("Prologue"))
    }

    @Test
    fun aScriptStuckInALoopIsStoppedAndClosesWithoutWaiting() =
        runBlocking<Unit> {
            val looping =
                JsSource(
                    info,
                    "class DefaultExtension extends MProvider { async getPopular() { while (true) {} } }",
                    Site(),
                    callTimeoutMillis = 1_000,
                )
            val started = System.currentTimeMillis()
            assertFailsWith<ExtensionError> { looping.popular(1) }
            looping.close()
            assertTrue(System.currentTimeMillis() - started < 70_000)
        }

    @Test
    fun switchingReadingSourceStartsAFreshBaseline() {
        val store =
            org.nekomanga.shared.library.LibraryStore(
                java.io.File(java.nio.file.Files.createTempDirectory("b").toFile(), "l.json"),
            )
        val title =
            org.nekomanga.shared.model.Title(org.nekomanga.shared.model.Sources.COMICK, "h", "T")
        store.add(title)
        val md =
            org.nekomanga.shared.model.Chapter(
                "mangadex",
                "a",
                "1",
                language = "en",
                publishedAt = "",
            )
        store.recordCheck(org.nekomanga.shared.library.LibraryEntry.keyOf(title), listOf(md))
        store.switchReadingSource(title, "ext:1")
        val ext =
            org.nekomanga.shared.model.Chapter(
                "ext:1",
                "/c/1",
                "1",
                language = "en",
                publishedAt = "",
            )
        val fresh =
            store.recordCheck(org.nekomanga.shared.library.LibraryEntry.keyOf(title), listOf(ext))
        assertTrue(fresh.isEmpty(), "a new source's chapters aren't all new")
        assertEquals("ext:1", store.entry(title)!!.readingSource)
    }

    @Test
    fun reopeningATitleDoesNotSearchEverySourceAgain() {
        runBlocking {
            val dir = java.nio.file.Files.createTempDirectory("beako-cache").toFile()
            val index =
                """[{"name":"Sample","id":1,"lang":"en","baseUrl":"https://sample.test",
                    "sourceCodeUrl":"https://repo.test/sample.js","sourceCodeLanguage":1}]"""
            val http = FakeHttp { url ->
                if (url.endsWith("index.json")) json(index) else json(code)
            }
            val site = Site()
            val manager = org.nekomanga.shared.extensions.ExtensionManager(dir, http, site)
            manager.install(
                manager.addRepository("https://repo.test/index.json").single(),
                "https://repo.test/index.json",
            )
            val sources =
                org.nekomanga.shared.extensions.ReadingSources(
                    org.nekomanga.shared.mangadex.MangaDexApi(http),
                    manager,
                )
            val found =
                org.nekomanga.shared.model.Title(
                    org.nekomanga.shared.model.Sources.COMICK,
                    "a",
                    "Solo Tower",
                )
            val missing =
                org.nekomanga.shared.model.Title(
                    org.nekomanga.shared.model.Sources.COMICK,
                    "b",
                    "Nowhere",
                )

            assertEquals(1, sources.find(found).size)
            sources.find(missing)
            val searches = site.requests.count { it.url.contains("/search") }

            assertEquals(1, sources.find(found).size, "a match is kept")
            assertTrue(sources.find(missing).isEmpty(), "and so is a miss")
            assertEquals(searches, site.requests.count { it.url.contains("/search") })
            manager.closeAll()
        }
    }

    @Test
    fun missesExpireSoTitlesAddedLaterAreFound() {
        var now = 0L
        val cache =
            org.nekomanga.shared.extensions.SearchCache(missTtlMillis = 1_000, now = { now })
        val match = org.nekomanga.shared.extensions.SourceMatch("ext:1", "Sample", "/t")
        cache.put("t", "ext:1", match)
        cache.put("u", "ext:1", null)
        now = 5_000
        assertEquals(match, cache.get("t", "ext:1")?.match)
        assertNull(cache.get("u", "ext:1"))
    }
}
