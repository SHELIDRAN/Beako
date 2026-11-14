package org.nekomanga.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.state.DetailsModel
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.download.DownloadManager
import org.nekomanga.shared.download.ImageFetcher
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsModelTest {

    /** A real MangaDex feed: four chapters by Cosmic Scans, one by NexComic. */
    private val feed = javaClass.getResource("/fixtures/feed.json")!!.readText()

    @Test
    fun hidingAGroupFiltersTheListAndTheReaderAndIsSavedForLibraryTitles() = runTest {
        val dir = Files.createTempDirectory("beako").toFile()
        val http = HttpFetcher { url ->
            HttpResponse(200, "application/json", if (url.contains("/feed")) feed else "[]")
        }
        val mangaDex = MangaDexApi(http)
        val library = LibraryStore(File(dir, "library.json"))
        val title = Title(Sources.MANGADEX, "m", "Sample")
        val model =
            DetailsModel(
                backgroundScope,
                Discovery(ComickApi(http), mangaDex),
                mangaDex,
                library,
                DownloadManager(
                    dir,
                    backgroundScope,
                    { error("unused") },
                    ImageFetcher { error("unused") },
                ),
                title,
            )
        model.start()
        runCurrent()

        assertEquals(listOf("Cosmic Scans", "NexComic"), model.groups())
        assertEquals(5, model.readingOrder().size)

        model.addToLibrary()
        model.setGroupHidden("NexComic", hidden = true)
        assertEquals(4, model.readingOrder().size)
        assertTrue(model.visibleChapters().none { it.group == "NexComic" })
        assertEquals(setOf("NexComic"), library.entry(title)!!.excludedGroups)

        model.setGroupHidden("NexComic", hidden = false)
        assertEquals(5, model.visibleChapters().size)
    }

    /** The sample site the test extension reads. */
    private val site =
        org.nekomanga.shared.extensions.ExtensionTransport { request ->
            val html =
                when {
                    request.url.contains("/search") ->
                        """<div class="item"><a href="/title/1"><img src="https://img.test/1.jpg"><h3>Solo Tower</h3></a></div>"""
                    request.url.endsWith("/title/1") ->
                        """<h1>Solo Tower</h1><ul><li class="chapter"><a href="/c/2">Chapter 2</a></li>
                           <li class="chapter"><a href="/c/1">Chapter 1</a></li></ul>"""
                    else -> ""
                }
            org.nekomanga.shared.extensions.ExtensionResponse(200, html, emptyMap(), request.url)
        }

    @Test
    fun titlesMissingFromMangaDexReadFromAnInstalledSource() =
        kotlinx.coroutines.runBlocking<Unit> {
            // Real time: the extension runs on its own thread and has real timeouts.
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            val dir = Files.createTempDirectory("beako").toFile()
            val code = javaClass.getResource("/fixtures/sample-extension.js")!!.readText()
            val http = HttpFetcher { url ->
                when {
                    url.endsWith("index.json") ->
                        HttpResponse(
                            200,
                            "application/json",
                            """[{"name":"Sample","id":1,"lang":"en","baseUrl":"https://sample.test","sourceCodeUrl":"https://repo.test/s.js","sourceCodeLanguage":1}]""",
                        )
                    url.endsWith("s.js") -> HttpResponse(200, "text/javascript", code)
                    // MangaDex finds nothing.
                    else ->
                        HttpResponse(
                            200,
                            "application/json",
                            """{"result":"ok","data":[],"total":0,"limit":10,"offset":0}""",
                        )
                }
            }
            val manager =
                org.nekomanga.shared.extensions.ExtensionManager(File(dir, "ext"), http, site)
            val info = manager.addRepository("https://repo.test/index.json").single()
            manager.install(info, "https://repo.test/index.json")
            val mangaDex = MangaDexApi(http)
            val title = Title(Sources.COMICK, "hid", "Solo Tower")
            val library = LibraryStore(File(dir, "library.json")).apply { add(title) }
            val model =
                DetailsModel(
                    scope,
                    Discovery(ComickApi(http), mangaDex),
                    mangaDex,
                    library,
                    DownloadManager(
                        dir,
                        scope,
                        { error("unused") },
                        ImageFetcher { error("unused") },
                    ),
                    title,
                    readingSources =
                        org.nekomanga.shared.extensions.ReadingSources(mangaDex, manager),
                )
            model.start()
            kotlinx.coroutines.withTimeout(15_000) {
                while (model.readingOrder().isEmpty()) kotlinx.coroutines.delay(20)
            }

            assertEquals(listOf("Sample"), model.availableOn.map { it.name })
            assertEquals("ext:1", model.selectedSource)
            assertEquals(listOf("1", "2"), model.readingOrder().map { it.number })
            // Remembered for the update check and the next visit.
            assertEquals("/title/1", library.entry(title)!!.sourceLinks["ext:1"])
            manager.closeAll()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun chaptersMissingFromTheChosenSourceAreFilledFromAnother() =
        kotlinx.coroutines.runBlocking<Unit> {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            val dir = Files.createTempDirectory("beako").toFile()
            val code = javaClass.getResource("/fixtures/sample-extension.js")!!.readText()
            val site =
                org.nekomanga.shared.extensions.ExtensionTransport { request ->
                    val html =
                        when {
                            request.url.contains("/search") ->
                                """<div class="item"><a href="/title/1"><h3>Solo Tower</h3></a></div>"""
                            request.url.endsWith("/title/1") ->
                                """<h1>Solo Tower</h1><ul><li class="chapter"><a href="/c/999">Chapter 999</a></li></ul>"""
                            else -> ""
                        }
                    org.nekomanga.shared.extensions.ExtensionResponse(
                        200,
                        html,
                        emptyMap(),
                        request.url,
                    )
                }
            val http = HttpFetcher { url ->
                when {
                    url.endsWith("index.json") ->
                        HttpResponse(
                            200,
                            "application/json",
                            """[{"name":"Sample","id":1,"lang":"en","sourceCodeUrl":"https://repo.test/s.js","sourceCodeLanguage":1}]""",
                        )
                    url.endsWith("s.js") -> HttpResponse(200, "text/javascript", code)
                    url.contains("/feed") -> HttpResponse(200, "application/json", feed)
                    else -> HttpResponse(200, "application/json", "[]")
                }
            }
            val manager =
                org.nekomanga.shared.extensions.ExtensionManager(File(dir, "ext"), http, site)
            manager.install(
                manager.addRepository("https://repo.test/index.json").single(),
                "https://repo.test/index.json",
            )
            val mangaDex = MangaDexApi(http)
            val title = Title(Sources.MANGADEX, "m", "Solo Tower")
            val model =
                DetailsModel(
                    scope,
                    Discovery(ComickApi(http), mangaDex),
                    mangaDex,
                    LibraryStore(File(dir, "library.json")),
                    DownloadManager(
                        dir,
                        scope,
                        { error("unused") },
                        ImageFetcher { error("unused") },
                    ),
                    title,
                    readingSources =
                        org.nekomanga.shared.extensions.ReadingSources(mangaDex, manager),
                )
            model.start()
            kotlinx.coroutines.withTimeout(15_000) {
                while (model.visibleChapters().none { it.number == "999" }) kotlinx.coroutines
                    .delay(20)
            }

            assertEquals(Sources.MANGADEX, model.selectedSource)
            assertEquals(6, model.visibleChapters().size)
            assertEquals("999", model.visibleChapters().first().number)
            assertEquals("Sample", model.filledFrom["/c/999"])
            manager.closeAll()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
}
