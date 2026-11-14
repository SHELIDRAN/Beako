package org.nekomanga.shared.mangadex

import eu.kanade.tachiyomi.source.online.models.dto.MangaListDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.nekomanga.shared.auth.SecretVault
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.BeakoJson
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.HttpTransport
import org.nekomanga.shared.net.RateLimiter
import org.nekomanga.shared.net.int
import org.nekomanga.shared.net.sendJson
import org.nekomanga.shared.net.str

/** What a MangaDex personal API client sign-in needs. The password is used once, never stored. */
data class MangaDexCredentials(
    val username: String,
    val password: String,
    val clientId: String,
    val clientSecret: String,
)

/**
 * A MangaDex account, signed in through a personal API client (MangaDex's only option for
 * third-party apps today). The client secret and refresh token are kept in the [vault]; the client
 * ID and username are not secret and are passed in.
 */
class MangaDexAccount(
    private val http: HttpTransport,
    private val vault: SecretVault,
    private val now: () -> Long = System::currentTimeMillis,
    private val limiter: RateLimiter = RateLimiter(permits = 5, periodMillis = 1_000),
) {
    private val mutex = Mutex()
    private var accessToken: String? = null
    private var expiresAt = 0L
    private var clientId: String? = null

    private val _username = MutableStateFlow<String?>(null)

    /** The signed-in username, or null. */
    val username: StateFlow<String?> = _username.asStateFlow()

    val connected: Boolean
        get() = vault.get(REFRESH_KEY) != null

    /** Restores a saved sign-in (the client ID and username come from settings). */
    fun restore(savedClientId: String?, savedUsername: String?) {
        clientId = savedClientId
        _username.value = if (connected) savedUsername else null
    }

    suspend fun signIn(credentials: MangaDexCredentials) {
        val fields =
            listOf(
                "grant_type" to "password",
                "username" to credentials.username.trim(),
                "password" to credentials.password,
                "client_id" to credentials.clientId.trim(),
                "client_secret" to credentials.clientSecret.trim(),
            )
        mutex.withLock {
            val body =
                try {
                    tokenRequest(fields)
                } catch (e: ApiError.Rejected) {
                    throw ApiError.Rejected(
                        SERVICE,
                        e.code,
                        ": check the username, password and API client details (the client must be approved by MangaDex)",
                    )
                } catch (e: ApiError.Unauthorized) {
                    throw ApiError.Rejected(SERVICE, e.code, ": check the API client ID and secret")
                }
            vault.put(SECRET_KEY, credentials.clientSecret.trim())
            clientId = credentials.clientId.trim()
            store(body)
        }
        _username.value = credentials.username.trim()
    }

    fun signOut() {
        vault.delete(REFRESH_KEY)
        vault.delete(SECRET_KEY)
        accessToken = null
        expiresAt = 0
        _username.value = null
    }

    /** Every followed title, newest follow first. */
    suspend fun follows(): List<Title> {
        val all = mutableListOf<Title>()
        var offset = 0
        while (true) {
            val url = "$API/user/follows/manga?limit=100&offset=$offset&includes%5B%5D=cover_art"
            val json = send(HttpRequest("GET", url)) ?: break
            val page =
                try {
                    BeakoJson.decodeFromJsonElement(MangaListDto.serializer(), json)
                } catch (e: SerializationException) {
                    throw ApiError.Unreadable(SERVICE, e)
                }
            all += page.data.map(MangaDexApi::toTitle)
            offset += page.data.size
            if (page.data.isEmpty() || offset >= page.total || offset >= MAX_FOLLOWS) break
        }
        return all
    }

    /** Marks chapters read on MangaDex. */
    suspend fun markRead(mangaId: String, chapterIds: List<String>) {
        if (chapterIds.isEmpty()) return
        val body = buildJsonObject {
            putJsonArray("chapterIdsRead") {
                chapterIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
        }
        send(HttpRequest.json("POST", "$API/manga/$mangaId/read", body.toString()))
    }

    private suspend fun send(request: HttpRequest): JsonElement? {
        limiter.acquire()
        val token = validToken(rejected = null)
        return try {
            http.sendJson(SERVICE, request.withToken(token))
        } catch (e: ApiError.Unauthorized) {
            if (e.code != 401) throw e
            val fresh = validToken(rejected = token)
            limiter.acquire()
            http.sendJson(SERVICE, request.withToken(fresh))
        }
    }

    private suspend fun validToken(rejected: String?): String = mutex.withLock {
        accessToken?.let { if (it != rejected && now() < expiresAt) return it }
        val refresh = vault.get(REFRESH_KEY)
        val secret = vault.get(SECRET_KEY)
        val id = clientId
        if (refresh == null || secret == null || id == null)
            throw ApiError.Unauthorized(SERVICE, 401)
        val body =
            try {
                tokenRequest(
                    listOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to refresh,
                        "client_id" to id,
                        "client_secret" to secret,
                    ),
                )
            } catch (e: ApiError.Rejected) {
                if (e.code == 400 || e.code == 401) signOut()
                throw ApiError.Unauthorized(SERVICE, 401)
            } catch (e: ApiError.Unauthorized) {
                signOut()
                throw e
            }
        store(body)
        accessToken ?: throw ApiError.Unauthorized(SERVICE, 401)
    }

    private suspend fun tokenRequest(fields: List<Pair<String, String>>): JsonElement =
        http.sendJson(SERVICE, HttpRequest.form(TOKEN_URL, fields))
            ?: throw ApiError.Unreadable(SERVICE, IllegalStateException("empty token response"))

    private fun store(body: JsonElement) {
        accessToken =
            body.str("access_token")
                ?: throw ApiError.Unreadable(SERVICE, IllegalStateException("no access token"))
        body.str("refresh_token")?.let { vault.put(REFRESH_KEY, it) }
        expiresAt = now() + (body.int("expires_in") ?: 900) * 1000L - 60_000L
    }

    private fun HttpRequest.withToken(token: String) =
        copy(headers = headers + ("Authorization" to "Bearer $token"))

    companion object {
        const val SERVICE = "MangaDex"
        const val API = "https://api.mangadex.org"
        const val TOKEN_URL =
            "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token"
        const val REFRESH_KEY = "mangadex.refresh_token"
        const val SECRET_KEY = "mangadex.client_secret"
        private const val MAX_FOLLOWS = 10_000
    }
}
