package org.nekomanga.shared.tracking.comick

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.nekomanga.shared.auth.buildQuery
import org.nekomanga.shared.library.Track
import org.nekomanga.shared.library.TrackStatus
import org.nekomanga.shared.library.Trackers
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.ExternalIds
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.double
import org.nekomanga.shared.net.get
import org.nekomanga.shared.net.int
import org.nekomanga.shared.net.items
import org.nekomanga.shared.net.str
import org.nekomanga.shared.tracking.Account
import org.nekomanga.shared.tracking.RemoteEntry
import org.nekomanga.shared.tracking.Tracker
import org.nekomanga.shared.tracking.TrackerMatch

/** Comick as a tracker, through its Library API (`/integrations/v1`). */
class ComickTracker(private val connection: ComickAccount) : Tracker {
    override val service = Trackers.COMICK
    override val displayName = "Comick"

    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account.asStateFlow()

    /** Loads the connected account's name and avatar; clears it when not connected. */
    suspend fun refreshAccount() {
        if (!connection.connected.value) {
            _account.value = null
            return
        }
        val me = connection.send(read("me"))
        _account.value = Account(me.str("username") ?: "Comick", me.str("avatar_url"))
    }

    override suspend fun find(title: Title): TrackerMatch? {
        // A Comick title already is its own record.
        if (title.source == Sources.COMICK)
            return TrackerMatch(title.id, title.title, title.coverUrl, title.year)
        val names = (listOf(title.title) + title.altTitles).filter { it.length in 2..100 }.take(3)
        for (name in names) {
            val found = lookup(name)
            found
                .firstOrNull { it.second.sharesIdWith(title.externalIds) }
                ?.let {
                    return it.first
                }
            found
                .firstOrNull { same(it.first.title, title) }
                ?.let {
                    return it.first
                }
        }
        return null
    }

    override suspend fun search(query: String): List<TrackerMatch> =
        if (query.trim().length < 2) emptyList() else lookup(query.trim()).map { it.first }

    private suspend fun lookup(query: String): List<Pair<TrackerMatch, ExternalIds>> {
        val params =
            buildQuery(listOf("q" to query.take(100), "media_type" to "manga", "limit" to "10"))
        return connection.send(read("titles?$params"))["data"].items().map { t ->
            val ext = t["external_ids"]
            TrackerMatch(
                t.str("hid").orEmpty(),
                t.str("title").orEmpty(),
                t.str("cover_url"),
                t.int("year"),
            ) to
                ExternalIds(
                    ext.str("anilist"),
                    ext.str("myanimelist"),
                    ext.str("mangaupdates"),
                    ext.str("mangabaka"),
                )
        }
    }

    override suspend fun get(remoteId: String): Track? =
        try {
            parseEntry(connection.send(read("me/library/$remoteId"))["data"])
        } catch (e: ApiError.Rejected) {
            if (e.code == 404) null else throw e
        }

    override suspend fun save(
        remoteId: String,
        status: TrackStatus?,
        progress: String?,
        score: Int?,
    ): Track {
        val body = buildJsonObject {
            status?.let { put("status", statusCode(it)) }
            progress?.let { put("progress", buildJsonObject { put("number", it) }) }
            score?.let { put("rating", it.coerceIn(1, 10)) }
        }
        val exists = get(remoteId) != null
        val method = if (exists) "PATCH" else "PUT"
        val sent =
            if (!exists && status == null)
                buildJsonObject {
                    put("status", statusCode(TrackStatus.READING))
                    body.forEach { (k, v) -> put(k, v) }
                }
            else body
        val response =
            connection.send(HttpRequest.json(method, url("me/library/$remoteId"), sent.toString()))
        return parseEntry(response["data"]) ?: Track(service, remoteId, status, progress, score)
    }

    /** Clears the rating on Comick (null clears; omitting a field keeps it). */
    suspend fun clearScore(remoteId: String) {
        val body = buildJsonObject { put("rating", JsonNull) }
        connection.send(HttpRequest.json("PATCH", url("me/library/$remoteId"), body.toString()))
    }

    override suspend fun remove(remoteId: String) {
        connection.send(HttpRequest("DELETE", url("me/library/$remoteId")))
    }

    override suspend fun list(): List<RemoteEntry> {
        val all = mutableListOf<RemoteEntry>()
        var cursor: String? = null
        do {
            val query =
                buildQuery(listOfNotNull("media_type" to "manga", cursor?.let { "cursor" to it }))
            val page = connection.send(read("me/library?$query"))
            page["data"].items().forEach { e ->
                val track = parseEntry(e) ?: return@forEach
                val country = e.str("country")
                val title =
                    Title(
                        source = Sources.COMICK,
                        id = track.remoteId,
                        title = e.str("title").orEmpty(),
                        slug = e.str("slug"),
                        altTitles = e["titles"].items().mapNotNull { it.str("title") }.distinct(),
                        coverUrl = e.str("cover_url"),
                        country = country,
                        type = ComicType.fromOrigin(country),
                        rating = e.double("bayesian_rating"),
                    )
                all += RemoteEntry(title, track)
            }
            cursor = page.str("next_cursor")
        } while (cursor != null && all.size < MAX_IMPORT)
        return all
    }

    override suspend fun disconnect() {
        connection.disconnect()
        _account.value = null
    }

    private fun parseEntry(e: JsonElement?): Track? {
        val hid = e.str("hid") ?: return null
        return Track(
            service = service,
            remoteId = hid,
            status = statusOf(e.int("status")),
            progress = e["progress"].str("number"),
            score = e.double("rating")?.let { kotlin.math.round(it).toInt() },
            remoteTitle = e.str("title"),
        )
    }

    private fun same(name: String, title: Title): Boolean {
        val key = normalise(name)
        return key.isNotEmpty() &&
            (listOf(title.title) + title.altTitles).any { normalise(it) == key }
    }

    private fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private fun read(path: String) = HttpRequest("GET", url(path))

    private fun url(path: String) = "${ComickAccount.RESOURCE}/$path"

    companion object {
        private const val MAX_IMPORT = 5_000

        fun statusCode(status: TrackStatus): Int =
            when (status) {
                TrackStatus.READING -> 1
                TrackStatus.COMPLETED -> 2
                TrackStatus.ON_HOLD -> 3
                TrackStatus.DROPPED -> 4
                TrackStatus.PLAN_TO_READ -> 5
            }

        fun statusOf(code: Int?): TrackStatus? =
            when (code) {
                1 -> TrackStatus.READING
                2 -> TrackStatus.COMPLETED
                3 -> TrackStatus.ON_HOLD
                4 -> TrackStatus.DROPPED
                5 -> TrackStatus.PLAN_TO_READ
                else -> null
            }
    }
}
