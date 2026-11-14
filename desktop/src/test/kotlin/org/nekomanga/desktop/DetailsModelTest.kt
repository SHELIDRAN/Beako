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
}
