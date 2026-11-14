package org.nekomanga.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.screens.trackLabel
import org.nekomanga.desktop.state.AccountsModel
import org.nekomanga.desktop.state.Service
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.library.Trackers
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.HttpTransport
import org.nekomanga.shared.tracking.anilist.AniListTracker
import org.nekomanga.shared.tracking.comick.ComickAccount
import org.nekomanga.shared.tracking.comick.ComickTracker

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsModelTest {

    /** Answers like Comick's Library API: one entry, "hid", at chapter 2. */
    private class ComickFake : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var progress = "2"

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            val json =
                when {
                    request.url == ComickAccount.TOKEN_URL ->
                        """{"access_token":"a","expires_in":900,"refresh_token":"r2"}"""
                    request.url == ComickAccount.REVOKE_URL -> "{}"
                    request.url.contains("/me/library?") ->
                        """{"data":[${entry()}],"next_cursor":null}"""
                    request.url.endsWith("/me") -> """{"username":"reader"}"""
                    request.method == "PATCH" -> {
                        progress =
                            Regex(""""number":"([^"]+)"""").find(request.body!!)!!.groupValues[1]
                        """{"data":${entry()}}"""
                    }
                    else -> """{"data":${entry()}}"""
                }
            return HttpResponse(200, "application/json", json)
        }

        private fun entry() =
            """{"hid":"hid","title":"Sample","status":1,"rating":null,"progress":{"unit":"chapter","number":"$progress"}}"""
    }

    private val title = Title(Sources.COMICK, "hid", "Sample")

    private fun chapter(number: String) =
        Chapter(Sources.MANGADEX, "c$number", number, language = "en", publishedAt = "2026-01-01")

    private fun model(
        scope: kotlinx.coroutines.CoroutineScope,
        fake: ComickFake,
    ): Pair<AccountsModel, LibraryStore> {
        val dir = Files.createTempDirectory("beako").toFile()
        val vault = MemoryVault().apply { put(ComickAccount.REFRESH_KEY, "r1") }
        val library = LibraryStore(File(dir, "library.json"))
        val unused = HttpTransport { HttpResponse(500, "text/plain", "") }
        val comickAccount = ComickAccount(fake, vault)
        val model =
            AccountsModel(
                scope,
                comickAccount,
                ComickTracker(comickAccount),
                AniListTracker(unused, vault),
                MangaDexAccount(unused, vault),
                library,
                SettingsStore(File(dir, "settings.json")),
                openBrowser = {},
            )
        return model to library
    }

    @Test
    fun importingTheComickLibraryAddsAndLinksTitles() = runTest {
        val fake = ComickFake()
        val (model, library) = model(backgroundScope, fake)
        model.import(Service.COMICK)
        runCurrent()

        assertEquals("Imported 1 titles", model.status[Service.COMICK]?.message)
        val track = library.entry(title)!!.tracks[Trackers.COMICK]!!
        assertEquals("hid", track.remoteId)
        assertEquals("2", track.progress)
    }

    @Test
    fun finishingChaptersPushesTheHighestToLinkedTrackers() = runTest {
        val fake = ComickFake()
        val (model, library) = model(backgroundScope, fake)
        model.comick.refreshAccount()
        library.add(title)
        library.setTrack(title, Track(Trackers.COMICK, "hid", TrackStatus.READING, "2"))

        model.chaptersRead(title, listOf(chapter("3"), chapter("5"), chapter("4")))
        runCurrent()

        assertEquals("5", fake.progress)
        assertEquals("5", library.entry(title)!!.tracks[Trackers.COMICK]!!.progress)
        assertNull(model.syncProblem)
    }

    @Test
    fun disconnectingForgetsTheTokenAndTheLinks() = runTest {
        val fake = ComickFake()
        val (model, library) = model(backgroundScope, fake)
        library.add(title)
        library.setTrack(title, Track(Trackers.COMICK, "hid"))

        model.disconnect(Service.COMICK)
        runCurrent()

        assertTrue(library.entry(title)!!.tracks.isEmpty())
        assertEquals(false, model.comickAccount.connected.value)
    }

    @Test
    fun trackLabelsReadNaturally() {
        assertEquals(
            "Comick · Reading · Ch. 12 · ★ 8",
            trackLabel("Comick", Track(Trackers.COMICK, "h", TrackStatus.READING, "12", 8)),
        )
        assertEquals("AniList", trackLabel("AniList", Track(Trackers.ANILIST, "1")))
    }
}
