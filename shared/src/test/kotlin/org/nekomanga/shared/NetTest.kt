package org.nekomanga.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.net.RateLimiter
import org.nekomanga.shared.net.buildUrl
import org.nekomanga.shared.net.getJson

class NetTest {

    @Test
    fun rateLimiterMakesTheCallOverTheLimitWait() = runTest {
        var clock = 0L
        val waits = mutableListOf<Long>()
        val limiter =
            RateLimiter(
                permits = 2,
                periodMillis = 1_000,
                now = { clock },
                sleep = {
                    waits += it
                    clock += it
                },
            )
        limiter.acquire()
        clock = 400
        limiter.acquire()
        assertTrue(waits.isEmpty())
        clock = 500
        limiter.acquire()
        // The first call was at 0, so the third waits until 1000.
        assertEquals(listOf(500L), waits)
    }

    @Test
    fun buildUrlRepeatsParametersAndEncodesThem() {
        val url =
            buildUrl(
                "https://api.example.org",
                "v1.0/search/",
                listOf("tags" to "a b", "tags" to "c", "q" to "x&y"),
            )
        assertEquals("https://api.example.org/v1.0/search/?tags=a%20b&tags=c&q=x%26y", url)
    }

    @Test
    fun networkFailureIsUnreachable() = runTest {
        val error = assertFailsWith<ApiError> { offline.getJson("Comick", "https://x.test/") }
        assertIs<ApiError.Unreachable>(error)
        assertTrue(error.isOutage)
    }

    @Test
    fun htmlChallengeIsRecognisedBeforeParsing() = runTest {
        assertFailsWith<ApiError.BlockedByChallenge> {
            cloudflare.getJson("Comick", "https://x.test/")
        }
        // Even with a 200 status and a JSON content type, an HTML body is a challenge.
        val sneaky = FakeHttp { HttpResponse(200, "application/json", "  <html></html>") }
        assertFailsWith<ApiError.BlockedByChallenge> { sneaky.getJson("Comick", "https://x.test/") }
    }

    @Test
    fun statusCodesMapToErrors() = runTest {
        val limited = FakeHttp { json("{}", 429, mapOf("retry-after" to "30")) }
        val rateLimited =
            assertFailsWith<ApiError.RateLimited> { limited.getJson("Comick", "https://x.test/") }
        assertEquals(30, rateLimited.retryAfterSeconds)

        val broken = FakeHttp { json("{}", 503) }
        assertFailsWith<ApiError.ServerError> { broken.getJson("Comick", "https://x.test/") }

        val bad = FakeHttp { json("""{"message":"limit too high"}""", 400) }
        val rejected =
            assertFailsWith<ApiError.Rejected> { bad.getJson("Comick", "https://x.test/") }
        assertTrue(rejected.message!!.endsWith(": limit too high"))
        assertTrue(!rejected.isOutage)

        val garbage = FakeHttp { json("not json {") }
        assertFailsWith<ApiError.Unreadable> { garbage.getJson("Comick", "https://x.test/") }
    }

    @Test
    fun connectionFailuresSayWhyAndAreLogged() {
        kotlinx.coroutines.test.runTest {
            val dir = java.nio.file.Files.createTempDirectory("beako-net").toFile()
            org.nekomanga.shared.diagnostics.DiagnosticLog.start(dir)
            val blocked = FakeHttp { throw java.net.UnknownHostException("api.comick.dev") }
            val error =
                kotlin.test.assertFailsWith<org.nekomanga.shared.net.ApiError.Unreachable> {
                    blocked.getJson("Comick", "https://api.comick.dev/v1.0/search/?q=a")
                }
            kotlin.test.assertTrue(
                error.message!!.contains("address couldn’t be found"),
                error.message,
            )
            kotlin.test.assertTrue(error.message!!.contains("DNS over HTTPS"))
            val log = java.io.File(dir, "beako.log").readText()
            kotlin.test.assertTrue(
                log.contains("Comick request failed: GET https://api.comick.dev/v1.0/search/?q=a"),
                log,
            )
            kotlin.test.assertTrue(log.contains("java.net.UnknownHostException"))

            val tls = FakeHttp { throw javax.net.ssl.SSLHandshakeException("bad cert") }
            val tlsError =
                kotlin.test.assertFailsWith<org.nekomanga.shared.net.ApiError.Unreachable> {
                    tls.getJson("Comick", "https://api.comick.dev/top")
                }
            kotlin.test.assertTrue(
                tlsError.message!!.contains("secure connection failed"),
                tlsError.message,
            )
        }
    }
}
