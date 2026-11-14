package org.nekomanga.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.auth.parseQuery
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.library.Trackers
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexCredentials
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.HttpTransport
import org.nekomanga.shared.tracking.Account
import org.nekomanga.shared.tracking.RemoteEntry
import org.nekomanga.shared.tracking.Tracker
import org.nekomanga.shared.tracking.TrackerMatch
import org.nekomanga.shared.tracking.TrackerSync
import org.nekomanga.shared.tracking.anilist.AniListTracker
import org.nekomanga.shared.tracking.comick.ComickAccount
import org.nekomanga.shared.tracking.comick.ComickTracker

class TrackingTest {

    /** A tracker holding one remote record in memory. */
    private class FakeTracker(var remote: Track?) : Tracker {
        override val service = Trackers.COMICK
        override val displayName = "Fake"
        override val account: StateFlow<Account?> = MutableStateFlow(Account("me"))
        override val isConnected = true
        val saves = mutableListOf<Track>()

        override suspend fun find(title: Title) = null

        override suspend fun search(query: String) = emptyList<TrackerMatch>()

        override suspend fun get(remoteId: String) = remote

        override suspend fun save(
            remoteId: String,
            status: TrackStatus?,
            progress: String?,
            score: Int?,
        ): Track {
            val track = Track(service, remoteId, status, progress, score)
            saves += track
            remote = track
            return track
        }

        override suspend fun remove(remoteId: String) {
            remote = null
        }

        override suspend fun list() = emptyList<RemoteEntry>()

        override suspend fun disconnect() = Unit
    }

    private val title = Title(Sources.COMICK, "hid", "Sample")

    private fun library() =
        LibraryStore(File(Files.createTempDirectory("beako").toFile(), "library.json")).apply {
            add(title)
        }

    @Test
    fun progressComparisonPreservesUnknownAndNonNumericProgress() {
        assertTrue(TrackerSync.isAhead("12.5", "12"))
        assertTrue(TrackerSync.isAhead("1", null))
        assertFalse(TrackerSync.isAhead("12", "12"))
        assertFalse(TrackerSync.isAhead("3", "extra"))
        assertFalse(TrackerSync.isAhead(null, "1"))
    }

    @Test
    fun linkingAddsMissingTitlesAndKeepsExistingProgress() = runTest {
        val library = library()
        val missing = FakeTracker(remote = null)
        assertEquals(
            TrackStatus.READING,
            TrackerSync(library).link(missing, title, "hid", "4").status,
        )

        val ahead =
            FakeTracker(remote = Track(Trackers.COMICK, "hid", TrackStatus.ON_HOLD, "40", 8))
        val linked = TrackerSync(library).link(ahead, title, "hid", "4")
        assertEquals("40", linked.progress)
        assertTrue(ahead.saves.isEmpty(), "remote progress must never be lowered")
        assertEquals("40", library.entry(title)!!.tracks[Trackers.COMICK]!!.progress)
    }

    @Test
    fun finishingAChapterPushesItOnlyWhenAhead() = runTest {
        val library = library()
        val tracker =
            FakeTracker(remote = Track(Trackers.COMICK, "hid", TrackStatus.PLAN_TO_READ, "2", 9))
        val sync = TrackerSync(library)
        sync.link(tracker, title, "hid", null)

        assertEquals(listOf("3"), sync.chapterRead(listOf(tracker), title, "3").map { it.progress })
        // Plan to read moves to Reading, and the score is kept.
        assertEquals(TrackStatus.READING, tracker.remote!!.status)
        assertEquals(9, tracker.remote!!.score)

        // Progress made elsewhere (chapter 10) is never lowered by re-reading chapter 5.
        tracker.remote = tracker.remote!!.copy(progress = "10")
        assertTrue(sync.chapterRead(listOf(tracker), title, "5").isEmpty())
        assertEquals("10", library.entry(title)!!.tracks[Trackers.COMICK]!!.progress)
    }

    /** Records requests and answers library calls like Comick's Library API. */
    private class ComickLibraryFake : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var hasEntry = true

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            if (request.url == ComickAccount.TOKEN_URL) {
                return HttpResponse(
                    200,
                    "application/json",
                    """{"access_token":"a","expires_in":900,"refresh_token":"r2"}""",
                )
            }
            if (request.method == "GET" && request.url.endsWith("/me/library/hid") && !hasEntry) {
                return HttpResponse(404, "application/json", """{"message":"Not found"}""")
            }
            hasEntry = true
            return HttpResponse(
                200,
                "application/json",
                """{"data":{"hid":"hid","title":"Sample","status":1,"rating":8.4,"progress":{"unit":"chapter","hid":null,"number":"12.5","volume":null}}}""",
            )
        }
    }

    @Test
    fun comickTrackerReadsEntriesAndWritesWithTheRightMethods() = runTest {
        val fake = ComickLibraryFake()
        val tracker =
            ComickTracker(
                ComickAccount(fake, MemoryVault().apply { put(ComickAccount.REFRESH_KEY, "r1") }),
            )

        val track = tracker.get("hid")!!
        assertEquals(TrackStatus.READING, track.status)
        assertEquals("12.5", track.progress)
        assertEquals(8, track.score)

        tracker.save("hid", null, "13", null)
        val patch = fake.requests.last()
        assertEquals("PATCH", patch.method)
        assertEquals("""{"progress":{"number":"13"}}""", patch.body)

        fake.hasEntry = false
        tracker.save("hid", null, "1", null)
        val put = fake.requests.last()
        assertEquals("PUT", put.method)
        assertTrue(put.body!!.contains(""""status":1"""))
        assertTrue(
            fake.requests
                .filter { it.url != ComickAccount.TOKEN_URL }
                .all { it.headers["Authorization"] == "Bearer a" },
        )
    }

    @Test
    fun aniListMapsStatusesBothWays() {
        TrackStatus.entries.forEach {
            assertEquals(it, AniListTracker.statusOf(AniListTracker.statusName(it)))
        }
        assertEquals(TrackStatus.READING, AniListTracker.statusOf("REPEATING"))
        assertEquals(
            TrackStatus.READING,
            ComickTracker.statusOf(ComickTracker.statusCode(TrackStatus.READING)),
        )
    }

    @Test
    fun aniListMissingEntryIsNullAndSaveSendsScoreRaw() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val fake = HttpTransport { request ->
            requests += request
            val body = request.body.orEmpty()
            when {
                "Viewer" in body ->
                    HttpResponse(
                        200,
                        "application/json",
                        """{"data":{"Viewer":{"id":7,"name":"me","avatar":{"medium":null}}}}""",
                    )
                "MediaList(" in body ->
                    HttpResponse(
                        404,
                        "application/json",
                        """{"errors":[{"message":"Not Found.","status":404}],"data":{"MediaList":null}}""",
                    )
                else ->
                    HttpResponse(
                        200,
                        "application/json",
                        """{"data":{"SaveMediaListEntry":{"id":1,"status":"CURRENT","progress":12,"score":8,"media":{"title":{"romaji":"X"}}}}}""",
                    )
            }
        }
        val tracker =
            AniListTracker(fake, MemoryVault().apply { put(AniListTracker.TOKEN_KEY, "tok") })

        assertNull(tracker.get("123"))
        val saved = tracker.save("123", TrackStatus.READING, "12.5", 8)
        assertEquals("12", saved.progress)
        val mutation = requests.last().body!!
        assertTrue(
            """"p":12""" in mutation && """"r":80""" in mutation && """"s":"CURRENT"""" in mutation,
        )
        assertEquals("Bearer tok", requests.last().headers["Authorization"])
    }

    @Test
    fun mangaDexSignInUsesThePasswordOnceAndStoresOnlyTheSecretAndRefreshToken() = runTest {
        val bodies = mutableListOf<Map<String, String>>()
        val fake = HttpTransport { request ->
            bodies += parseQuery(request.body)
            HttpResponse(
                200,
                "application/json",
                """{"access_token":"a","expires_in":900,"refresh_token":"r"}""",
            )
        }
        val vault = MemoryVault()
        val account = MangaDexAccount(fake, vault)

        account.signIn(MangaDexCredentials("reader", "hunter2", "personal-client", "client-secret"))

        assertEquals("password", bodies.single()["grant_type"])
        assertEquals("r", vault.get(MangaDexAccount.REFRESH_KEY))
        assertEquals("client-secret", vault.get(MangaDexAccount.SECRET_KEY))
        assertEquals("reader", account.username.value)
        account.signOut()
        assertNull(vault.get(MangaDexAccount.REFRESH_KEY))
        assertNull(vault.get(MangaDexAccount.SECRET_KEY))
    }
}
