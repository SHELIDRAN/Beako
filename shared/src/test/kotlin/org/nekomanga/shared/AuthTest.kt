package org.nekomanga.shared

import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JHttpRequest
import java.net.http.HttpResponse as JHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.auth.AuthCancelled
import org.nekomanga.shared.auth.LoopbackReceiver
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.auth.Pkce
import org.nekomanga.shared.auth.parseQuery
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.HttpRequest
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.HttpTransport
import org.nekomanga.shared.tracking.comick.ComickAccount

class AuthTest {

    @Test
    fun pkceChallengeMatchesAnIndependentSha256() {
        // Computed separately with Python's hashlib and base64.
        assertEquals(
            "8C51z3_hbk2uTdvdyfFOVeC5tnI24Hlc7dHL-XJPDvA",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r2Wl6kpasjtovjE"),
        )
        val verifier = Pkce.randomToken()
        assertTrue(
            verifier.length == 43 &&
                verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' },
        )
        assertTrue(Pkce.randomToken() != verifier)
    }

    @Test
    fun requestsNeverPrintTheirBodies() {
        val request =
            HttpRequest.form("https://x.test/token", listOf("refresh_token" to "secret-value"))
        assertFalse("secret-value" in request.toString())
    }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun browse(url: String): String =
        HttpClient.newHttpClient()
            .send(JHttpRequest.newBuilder(URI(url)).build(), JHttpResponse.BodyHandlers.ofString())
            .body()

    @Test
    fun loopbackReceivesTheCallbackAndIgnoresOtherPaths() = runBlocking {
        val port = freePort()
        val callback =
            LoopbackReceiver(port, "/comick/callback").await {
                Thread {
                    browse("http://127.0.0.1:$port/favicon.ico")
                    // A prefetch with no OAuth answer must not end the wait.
                    browse("http://127.0.0.1:$port/comick/callback")
                    browse("http://127.0.0.1:$port/comick/callback?utm=x")
                    browse("http://127.0.0.1:$port/comick/callback?code=abc&state=s1")
                }
                    .start()
            }
        assertEquals(mapOf("code" to "abc", "state" to "s1"), callback.params)
    }

    @Test
    fun loopbackBridgesAnImplicitGrantFragment() = runBlocking {
        val port = freePort()
        val callback =
            LoopbackReceiver(port, "/anilist/callback", fragmentBridge = true).await {
                Thread {
                    // The browser first loads the page (the fragment stays in the browser)...
                    val page = browse("http://127.0.0.1:$port/anilist/callback")
                    check("location.hash" in page)
                    // ...whose script then sends the fragment back as a query.
                    browse(
                        "http://127.0.0.1:$port/anilist/callback/fragment?access_token=tok&token_type=Bearer",
                    )
                }
                    .start()
            }
        assertEquals("tok", callback.params["access_token"])
    }

    @Test
    fun loopbackTimesOut() = runBlocking {
        val error =
            assertFailsWith<AuthCancelled> {
                LoopbackReceiver(freePort(), "/cb", timeoutMillis = 50).await {}
            }
        assertTrue("timed out" in error.message!!)
    }

    /** A fake Comick: token endpoint and one library endpoint, with a rotating refresh token. */
    private class FakeComick : HttpTransport {
        var refreshToken = "r1"
        var accessToken = "a0"
        var tokenCalls = 0
        val tokenBodies = mutableListOf<String>()
        var expireAccessOnce = false
        var failRefreshWith: String? = null
        var refreshIoError = false

        override suspend fun send(request: HttpRequest): HttpResponse {
            if (request.url == ComickAccount.TOKEN_URL) {
                tokenCalls++
                tokenBodies += request.body.orEmpty()
                if (refreshIoError) throw IOException("connection reset")
                failRefreshWith?.let {
                    return HttpResponse(400, "application/json", """{"error":"$it"}""")
                }
                val fields = parseQuery(request.body)
                if (
                    fields["grant_type"] == "refresh_token" &&
                        fields["refresh_token"] != refreshToken
                ) {
                    return HttpResponse(400, "application/json", """{"error":"invalid_grant"}""")
                }
                refreshToken = "r${tokenCalls + 1}"
                accessToken = "a$tokenCalls"
                return HttpResponse(
                    200,
                    "application/json",
                    """{"access_token":"$accessToken","token_type":"Bearer","expires_in":900,"scope":"library:read library:write offline_access","refresh_token":"$refreshToken"}""",
                )
            }
            val auth = request.headers["Authorization"]
            if (auth != "Bearer $accessToken" || expireAccessOnce) {
                expireAccessOnce = false
                return HttpResponse(401, "application/json", """{"error":"invalid_token"}""")
            }
            return HttpResponse(
                200,
                "application/json",
                """{"id":"u","username":"reader","avatar_url":null}""",
            )
        }
    }

    @Test
    fun theRotatedRefreshTokenIsStoredAndOneRefreshServesManyCalls() = runTest {
        val fake = FakeComick()
        val vault = MemoryVault().apply { put(ComickAccount.REFRESH_KEY, "r1") }
        val account = ComickAccount(fake, vault)

        val results =
            (1..5)
                .map { async { account.send(HttpRequest("GET", "${ComickAccount.RESOURCE}/me")) } }
                .awaitAll()

        assertEquals(5, results.size)
        assertEquals(1, fake.tokenCalls)
        assertEquals(fake.refreshToken, vault.get(ComickAccount.REFRESH_KEY))
        val body = parseQuery(fake.tokenBodies.single())
        assertEquals("refresh_token", body["grant_type"])
        assertEquals(ComickAccount.CLIENT_ID, body["client_id"])
        assertEquals(ComickAccount.RESOURCE, body["resource"])
        assertNull(body["client_secret"])
    }

    @Test
    fun a401RefreshesOnceAndRetriesOnce() = runTest {
        val fake = FakeComick()
        val vault = MemoryVault().apply { put(ComickAccount.REFRESH_KEY, "r1") }
        val account = ComickAccount(fake, vault)
        account.send(HttpRequest("GET", "${ComickAccount.RESOURCE}/me"))
        fake.expireAccessOnce = true

        account.send(HttpRequest("GET", "${ComickAccount.RESOURCE}/me"))

        assertEquals(2, fake.tokenCalls)
    }

    @Test
    fun invalidGrantDisconnectsAndForgetsTheToken() = runTest {
        val fake = FakeComick().apply { failRefreshWith = "invalid_grant" }
        val vault = MemoryVault().apply { put(ComickAccount.REFRESH_KEY, "r1") }
        val account = ComickAccount(fake, vault)

        assertFailsWith<ApiError.Unauthorized> {
            account.send(HttpRequest("GET", "${ComickAccount.RESOURCE}/me"))
        }

        assertNull(vault.get(ComickAccount.REFRESH_KEY))
        assertFalse(account.connected.value)
    }

    @Test
    fun anAmbiguousRefreshFailureIsNotRetried() = runTest {
        val fake = FakeComick().apply { refreshIoError = true }
        val vault = MemoryVault().apply { put(ComickAccount.REFRESH_KEY, "r1") }
        val account = ComickAccount(fake, vault)

        assertFailsWith<ApiError.Unreachable> {
            account.send(HttpRequest("GET", "${ComickAccount.RESOURCE}/me"))
        }

        assertEquals(1, fake.tokenCalls)
        // The stored token is kept: it may still be valid.
        assertEquals("r1", vault.get(ComickAccount.REFRESH_KEY))
    }

    @Test
    fun signInValidatesStateAndExchangesTheCodeWithPkce() = runBlocking {
        val fake = FakeComick()
        val vault = MemoryVault()
        val account = ComickAccount(fake, vault)

        account.signIn { url ->
            val params = parseQuery(URI(url).rawQuery)
            assertEquals("S256", params["code_challenge_method"])
            assertEquals(ComickAccount.REDIRECT_URI, params["redirect_uri"])
            assertEquals(ComickAccount.SCOPES, params["scope"])
            Thread { browse("${ComickAccount.REDIRECT_URI}?code=c1&state=${params["state"]}") }
                .start()
        }

        val exchange = parseQuery(fake.tokenBodies.single())
        assertEquals("authorization_code", exchange["grant_type"])
        assertEquals("c1", exchange["code"])
        assertEquals(43, exchange["code_verifier"]!!.length)
        assertTrue(account.connected.value)
        assertEquals(fake.refreshToken, vault.get(ComickAccount.REFRESH_KEY))
    }

    @Test
    fun signInRejectsAMismatchedState() = runBlocking {
        val account = ComickAccount(FakeComick(), MemoryVault())
        assertFailsWith<AuthCancelled> {
            account.signIn {
                Thread { browse("${ComickAccount.REDIRECT_URI}?code=c1&state=forged") }.start()
            }
        }
        assertFalse(account.connected.value)
    }
}
