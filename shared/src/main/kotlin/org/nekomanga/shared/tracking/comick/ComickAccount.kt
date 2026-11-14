package org.nekomanga.shared.tracking.comick

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import org.nekomanga.shared.auth.AuthCancelled
import org.nekomanga.shared.auth.LoopbackReceiver
import org.nekomanga.shared.auth.Pkce
import org.nekomanga.shared.auth.SecretVault
import org.nekomanga.shared.auth.buildQuery
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.HttpTransport
import org.nekomanga.shared.net.RateLimiter
import org.nekomanga.shared.net.int
import org.nekomanga.shared.net.sendJson
import org.nekomanga.shared.net.str

/**
 * Beako's Comick connection: OAuth 2.0 with S256 PKCE as a public client (no secret), library
 * scopes, and a refresh token in the [vault]. Follows `docs/api/COMICK.md`:
 * - Refresh tokens rotate on every use with no grace period, so refreshes are serialised, the new
 *   token is stored before it is used, and a refresh that fails ambiguously is never retried.
 * - `invalid_grant` means the connection is gone: Beako forgets it and asks to reconnect.
 * - The access token stays in memory only.
 */
class ComickAccount(
    private val http: HttpTransport,
    private val vault: SecretVault,
    private val now: () -> Long = System::currentTimeMillis,
    /** Library reads and writes: 60 a minute per app and user, so stay under it. */
    private val limiter: RateLimiter = RateLimiter(permits = 50, periodMillis = 60_000),
) {
    private val mutex = Mutex()
    private var accessToken: String? = null
    private var expiresAt = 0L

    private val _connected = MutableStateFlow(vault.get(REFRESH_KEY) != null)

    /** True while a refresh token is stored. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Scopes the user granted at sign-in (they can decline write and offline access). */
    var grantedScopes: Set<String> = emptySet()
        private set

    /**
     * Signs in through the system browser: listens on the callback address, calls [openBrowser]
     * with the authorization URL, validates the returned state and exchanges the code.
     */
    suspend fun signIn(openBrowser: (String) -> Unit) {
        val verifier = Pkce.randomToken()
        val state = Pkce.randomToken()
        val url = authorizationUrl(state, Pkce.challenge(verifier))
        val callback =
            LoopbackReceiver(CALLBACK_PORT, CALLBACK_PATH).await { openBrowser(url) }.params
        if (callback["state"] != state)
            throw AuthCancelled("The sign-in response didn’t match. Try again.")
        callback["error"]?.let {
            throw AuthCancelled(
                if (it == "access_denied") "Comick access wasn’t allowed."
                else "Comick sign-in failed ($it).",
            )
        }
        val code = callback["code"] ?: throw AuthCancelled("Comick didn’t return a sign-in code.")
        val body = mutex.withLock {
            tokenRequest(
                listOf(
                    "grant_type" to "authorization_code",
                    "client_id" to CLIENT_ID,
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "code_verifier" to verifier,
                    "resource" to RESOURCE,
                ),
            )
        }
        store(body)
    }

    /** The authorization URL for an attempt with [state] and PKCE [challenge]. */
    fun authorizationUrl(state: String, challenge: String): String =
        AUTHORIZE_URL +
            "?" +
            buildQuery(
                listOf(
                    "response_type" to "code",
                    "client_id" to CLIENT_ID,
                    "redirect_uri" to REDIRECT_URI,
                    "scope" to SCOPES,
                    "resource" to RESOURCE,
                    "state" to state,
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256",
                ),
            )

    /**
     * Sends an authorised library request. On 401 it refreshes at most once and retries the request
     * once, as Comick's guide asks.
     */
    suspend fun send(request: HttpRequest): JsonElement? {
        limiter.acquire()
        val token = validAccessToken(forceRefresh = false)
        return try {
            http.sendJson(SERVICE, request.withToken(token))
        } catch (e: ApiError.Unauthorized) {
            if (e.code != 401) throw e
            val fresh = validAccessToken(forceRefresh = true, rejected = token)
            limiter.acquire()
            http.sendJson(SERVICE, request.withToken(fresh))
        }
    }

    /** Revokes both tokens with Comick and forgets them. */
    suspend fun disconnect() {
        val refresh = vault.get(REFRESH_KEY)
        val access = accessToken
        mutex.withLock { forget() }
        // Best effort: the local secrets are already gone.
        for ((token, hint) in listOf(refresh to "refresh_token", access to "access_token")) {
            if (token == null) continue
            runCatching {
                http.send(
                    HttpRequest.form(
                        REVOKE_URL,
                        listOf(
                            "token" to token,
                            "client_id" to CLIENT_ID,
                            "token_type_hint" to hint,
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun validAccessToken(forceRefresh: Boolean, rejected: String? = null): String =
        mutex.withLock {
            val current = accessToken
            // Another caller may have refreshed while this one waited.
            if (
                current != null &&
                    current != rejected &&
                    (!forceRefresh || rejected != null) &&
                    now() < expiresAt
            ) {
                return current
            }
            val refresh = vault.get(REFRESH_KEY) ?: throw notConnected()
            val body =
                try {
                    tokenRequest(
                        listOf(
                            "grant_type" to "refresh_token",
                            "client_id" to CLIENT_ID,
                            "refresh_token" to refresh,
                            "resource" to RESOURCE,
                        ),
                    )
                } catch (e: ApiError.Rejected) {
                    if (e.errorCode == "invalid_grant") {
                        forget()
                        throw notConnected()
                    }
                    throw e
                } catch (e: ApiError.Unauthorized) {
                    forget()
                    throw notConnected()
                }
            store(body)
            accessToken ?: throw notConnected()
        }

    private suspend fun tokenRequest(fields: List<Pair<String, String>>): JsonElement =
        http.sendJson(SERVICE, HttpRequest.form(TOKEN_URL, fields))
            ?: throw ApiError.Unreadable(SERVICE, IllegalStateException("empty token response"))

    /** Stores the rotated refresh token first, then the access token. */
    private fun store(body: JsonElement) {
        val access =
            body.str("access_token")
                ?: throw ApiError.Unreadable(SERVICE, IllegalStateException("no access token"))
        body.str("refresh_token")?.let { vault.put(REFRESH_KEY, it) }
        accessToken = access
        expiresAt = now() + (body.int("expires_in") ?: 900) * 1000L - EXPIRY_MARGIN
        grantedScopes = body.str("scope").orEmpty().split(' ').filter { it.isNotBlank() }.toSet()
        _connected.value = vault.get(REFRESH_KEY) != null || accessToken != null
    }

    private fun forget() {
        vault.delete(REFRESH_KEY)
        accessToken = null
        expiresAt = 0
        _connected.value = false
    }

    private fun notConnected() = ApiError.Unauthorized(SERVICE, 401, "invalid_grant")

    private fun HttpRequest.withToken(token: String) =
        copy(headers = headers + ("Authorization" to "Bearer $token"))

    companion object {
        const val SERVICE = "Comick"
        const val CLIENT_ID = "hySzjzgfgtlSwqnDsdXspsPdWTqnjVzW"
        const val CALLBACK_PORT = 47823
        const val CALLBACK_PATH = "/comick/callback"
        const val REDIRECT_URI = "http://127.0.0.1:47823/comick/callback"
        const val AUTHORIZE_URL = "https://comick.dev/api/auth/oauth2/authorize"
        const val TOKEN_URL = "https://comick.dev/api/auth/oauth2/token"
        const val REVOKE_URL = "https://comick.dev/api/auth/oauth2/revoke"
        const val RESOURCE = "https://api.comick.dev/integrations/v1"
        const val SCOPES = "library:read library:write offline_access"
        const val REFRESH_KEY = "comick.refresh_token"
        private const val EXPIRY_MARGIN = 60_000L
    }
}
