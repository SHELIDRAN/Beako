package org.nekomanga.shared.tracking.anilist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.nekomanga.shared.auth.AuthCancelled
import org.nekomanga.shared.auth.LoopbackReceiver
import org.nekomanga.shared.auth.Pkce
import org.nekomanga.shared.auth.SecretVault
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
import org.nekomanga.shared.net.HttpTransport
import org.nekomanga.shared.net.RateLimiter
import org.nekomanga.shared.net.double
import org.nekomanga.shared.net.get
import org.nekomanga.shared.net.int
import org.nekomanga.shared.net.items
import org.nekomanga.shared.net.sendJson
import org.nekomanga.shared.net.str
import org.nekomanga.shared.tracking.Account
import org.nekomanga.shared.tracking.RemoteEntry
import org.nekomanga.shared.tracking.Tracker
import org.nekomanga.shared.tracking.TrackerMatch

/**
 * AniList as a tracker, through its GraphQL API. Signs in with AniList's implicit grant (its only
 * flow for apps without a server secret): the token arrives in the callback URL's fragment, which
 * the loopback page hands back to Beako. Tokens last a year; there is no refresh.
 */
class AniListTracker(
    private val http: HttpTransport,
    private val vault: SecretVault,
    /** AniList allows 90 requests a minute; stay under it. */
    private val limiter: RateLimiter = RateLimiter(permits = 60, periodMillis = 60_000),
) : Tracker {
    override val service = Trackers.ANILIST
    override val displayName = "AniList"

    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account.asStateFlow()
    private var viewerId: Int? = null

    val connected: Boolean
        get() = vault.get(TOKEN_KEY) != null

    override val isConnected: Boolean
        get() = connected

    /** Signs in with the AniList API client [clientId], whose redirect URL is [REDIRECT_URI]. */
    suspend fun signIn(clientId: String, openBrowser: (String) -> Unit) {
        require(clientId.isNotBlank()) { "Enter your AniList client ID first." }
        val state = Pkce.randomToken()
        val url =
            AUTHORIZE_URL +
                "?" +
                buildQuery(
                    listOf(
                        "client_id" to clientId.trim(),
                        "response_type" to "token",
                        "state" to state,
                    ),
                )
        val params =
            LoopbackReceiver(CALLBACK_PORT, CALLBACK_PATH, fragmentBridge = true)
                .await { openBrowser(url) }
                .params
        // AniList echoes state; a mismatch means the response isn't from this attempt.
        params["error"]?.let { throw AuthCancelled("AniList sign-in failed ($it).") }
        // The state must come back unchanged: a callback without it may be forged by another page
        // or program trying to sign Beako into a different account.
        if (params["state"] != state) {
            throw AuthCancelled("The sign-in response didn’t match. Try again.")
        }
        val token =
            params["access_token"] ?: throw AuthCancelled("AniList didn’t return a sign-in token.")
        vault.put(TOKEN_KEY, token)
        try {
            refreshAccount()
        } catch (e: ApiError) {
            vault.delete(TOKEN_KEY)
            throw e
        }
    }

    suspend fun refreshAccount() {
        if (!connected) {
            _account.value = null
            return
        }
        val viewer = query("query { Viewer { id name avatar { medium } } }")["Viewer"]
        viewerId = viewer.int("id")
        _account.value = Account(viewer.str("name") ?: "AniList", viewer["avatar"].str("medium"))
    }

    override suspend fun find(title: Title): TrackerMatch? {
        title.externalIds.anilist?.toIntOrNull()?.let { id ->
            val media =
                query(
                    "query (\$id: Int) { Media(id: \$id, type: MANGA) { $MEDIA } }",
                    vars { put("id", id) },
                )["Media"]
            if (media != null) return match(media)
        }
        val names = (listOf(title.title) + title.altTitles).filter { it.isNotBlank() }.take(3)
        for (name in names) {
            val found = searchMedia(name)
            found
                .firstOrNull { sharesId(it, title.externalIds) }
                ?.let {
                    return match(it)
                }
            found
                .firstOrNull { sameName(it, title) }
                ?.let {
                    return match(it)
                }
        }
        return null
    }

    override suspend fun search(query: String): List<TrackerMatch> =
        if (query.isBlank()) emptyList() else searchMedia(query.trim()).map(::match)

    private suspend fun searchMedia(text: String): List<JsonElement> =
        query(
                "query (\$q: String) { Page(perPage: 10) { media(search: \$q, type: MANGA) { $MEDIA } } }",
                vars { put("q", text.take(100)) },
            )["Page"]["media"]
            .items()

    override suspend fun get(remoteId: String): Track? {
        val user = viewer()
        val result =
            query(
                "query (\$u: Int, \$m: Int) { MediaList(userId: \$u, mediaId: \$m, type: MANGA) { id status progress score(format: POINT_10) media { title { romaji } } } }",
                vars {
                    put("u", user)
                    put("m", remoteId.toInt())
                },
                notFoundIsNull = true,
            ) ?: return null
        return parseList(remoteId, result["MediaList"])
    }

    override suspend fun save(
        remoteId: String,
        status: TrackStatus?,
        progress: String?,
        score: Int?,
    ): Track {
        val result =
            query(
                "mutation (\$m: Int, \$s: MediaListStatus, \$p: Int, \$r: Int) { SaveMediaListEntry(mediaId: \$m, status: \$s, progress: \$p, scoreRaw: \$r) { id status progress score(format: POINT_10) media { title { romaji } } } }",
                vars {
                    put("m", remoteId.toInt())
                    status?.let { put("s", statusName(it)) }
                    progress?.toDoubleOrNull()?.let { put("p", it.toInt()) }
                    score?.let { put("r", it.coerceIn(1, 10) * 10) }
                },
            )
        return parseList(remoteId, result["SaveMediaListEntry"])
            ?: Track(service, remoteId, status, progress, score)
    }

    override suspend fun remove(remoteId: String) {
        val user = viewer()
        val entry =
            query(
                    "query (\$u: Int, \$m: Int) { MediaList(userId: \$u, mediaId: \$m, type: MANGA) { id } }",
                    vars {
                        put("u", user)
                        put("m", remoteId.toInt())
                    },
                    notFoundIsNull = true,
                )
                ?.get("MediaList")
                .int("id") ?: return
        query(
            "mutation (\$id: Int) { DeleteMediaListEntry(id: \$id) { deleted } }",
            vars { put("id", entry) },
        )
    }

    override suspend fun list(): List<RemoteEntry> {
        val user = viewer()
        val lists =
            query(
                "query (\$u: Int) { MediaListCollection(userId: \$u, type: MANGA) { lists { entries { status progress score(format: POINT_10) media { $MEDIA } } } } }",
                vars { put("u", user) },
            )["MediaListCollection"]["lists"]
        return lists
            .items()
            .flatMap { it["entries"].items() }
            .mapNotNull { e ->
                val media = e["media"]
                val id = media.int("id")?.toString() ?: return@mapNotNull null
                val track = parseList(id, e) ?: return@mapNotNull null
                RemoteEntry(toTitle(media ?: return@mapNotNull null), track)
            }
    }

    override suspend fun disconnect() {
        // AniList has no token revocation endpoint; users can revoke the app on AniList.
        vault.delete(TOKEN_KEY)
        viewerId = null
        _account.value = null
    }

    private suspend fun viewer(): Int =
        viewerId
            ?: run {
                refreshAccount()
                viewerId ?: throw ApiError.Unauthorized(SERVICE, 401)
            }

    private fun parseList(mediaId: String, e: JsonElement?): Track? {
        if (e !is JsonObject) return null
        return Track(
            service = service,
            remoteId = mediaId,
            status = statusOf(e.str("status")),
            progress = e.int("progress")?.toString(),
            score = e.double("score")?.takeIf { it > 0 }?.let { kotlin.math.round(it).toInt() },
            remoteTitle = e["media"]["title"].str("romaji"),
        )
    }

    private fun match(media: JsonElement) =
        TrackerMatch(
            media.int("id").toString(),
            media["title"].str("english") ?: media["title"].str("romaji").orEmpty(),
            media["coverImage"].str("large"),
            media["startDate"].int("year"),
        )

    private fun toTitle(media: JsonElement): Title {
        val names = media["title"]
        val main = names.str("english") ?: names.str("romaji").orEmpty()
        val country = media.str("countryOfOrigin")?.lowercase()
        return Title(
            source = Sources.ANILIST,
            id = media.int("id").toString(),
            title = main,
            altTitles =
                listOfNotNull(names.str("romaji"), names.str("native")).filter { it != main },
            coverUrl = media["coverImage"].str("large"),
            year = media["startDate"].int("year"),
            country = country,
            type = ComicType.fromOrigin(country),
            externalIds =
                ExternalIds(
                    anilist = media.int("id")?.toString(),
                    myAnimeList = media.int("idMal")?.toString(),
                ),
        )
    }

    private fun sharesId(media: JsonElement, ids: ExternalIds) =
        media.int("id")?.toString() == ids.anilist ||
            (ids.myAnimeList != null && media.int("idMal")?.toString() == ids.myAnimeList)

    private fun sameName(media: JsonElement, title: Title): Boolean {
        val wanted =
            (listOf(title.title) + title.altTitles)
                .map(::normalise)
                .filter { it.isNotEmpty() }
                .toSet()
        val names = media["title"]
        return listOfNotNull(names.str("english"), names.str("romaji"), names.str("native")).any {
            normalise(it) in wanted
        }
    }

    private fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private inline fun vars(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        buildJsonObject(block)

    /** Runs a GraphQL operation and returns its `data`. */
    private suspend fun query(
        text: String,
        variables: JsonObject = JsonObject(emptyMap()),
    ): JsonElement = query(text, variables, notFoundIsNull = false)!!

    private suspend fun query(
        text: String,
        variables: JsonObject,
        notFoundIsNull: Boolean,
    ): JsonElement? {
        limiter.acquire()
        val token = vault.get(TOKEN_KEY)
        val body = buildJsonObject {
            put("query", text)
            put("variables", variables)
        }
        val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
        val response =
            try {
                http.sendJson(SERVICE, HttpRequest.json("POST", ENDPOINT, body.toString(), headers))
            } catch (e: ApiError.Rejected) {
                // AniList answers a missing list entry with 404 and a GraphQL error.
                if (notFoundIsNull && e.code == 404) return null
                throw e
            } catch (e: ApiError.Unauthorized) {
                if (e.code == 401) {
                    vault.delete(TOKEN_KEY)
                    _account.value = null
                }
                throw e
            }
        val errors = response["errors"].items()
        if (errors.isNotEmpty()) {
            val status = errors.first().int("status")
            if (notFoundIsNull && status == 404) return null
            throw ApiError.Rejected(
                SERVICE,
                status ?: 400,
                ": ${errors.first().str("message")?.take(120)}",
            )
        }
        return response["data"]
    }

    companion object {
        const val SERVICE = "AniList"
        const val ENDPOINT = "https://graphql.anilist.co"
        const val AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize"
        const val CALLBACK_PORT = 47823
        const val CALLBACK_PATH = "/anilist/callback"
        const val REDIRECT_URI = "http://127.0.0.1:47823/anilist/callback"
        const val TOKEN_KEY = "anilist.access_token"

        /**
         * Beako's registered AniList API client (public; the implicit grant has no secret). Empty
         * until registered: users then enter their own client's id.
         */
        const val BEAKO_CLIENT_ID = ""
        private const val MEDIA =
            "id idMal title { romaji english native } coverImage { large } startDate { year } countryOfOrigin"

        fun statusName(status: TrackStatus): String =
            when (status) {
                TrackStatus.READING -> "CURRENT"
                TrackStatus.COMPLETED -> "COMPLETED"
                TrackStatus.ON_HOLD -> "PAUSED"
                TrackStatus.DROPPED -> "DROPPED"
                TrackStatus.PLAN_TO_READ -> "PLANNING"
            }

        fun statusOf(name: String?): TrackStatus? =
            when (name) {
                "CURRENT",
                "REPEATING" -> TrackStatus.READING
                "COMPLETED" -> TrackStatus.COMPLETED
                "PAUSED" -> TrackStatus.ON_HOLD
                "DROPPED" -> TrackStatus.DROPPED
                "PLANNING" -> TrackStatus.PLAN_TO_READ
                else -> null
            }
    }
}
