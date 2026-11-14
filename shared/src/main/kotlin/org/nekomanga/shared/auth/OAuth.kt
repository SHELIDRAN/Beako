package org.nekomanga.shared.auth

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** PKCE (RFC 7636) and the random values an authorization attempt needs. */
object Pkce {
    private val random = SecureRandom()

    /** A random 43-character base64url string (32 bytes). */
    fun randomToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The S256 challenge: base64url SHA-256 of [verifier], without padding. */
    fun challenge(verifier: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

fun buildQuery(params: List<Pair<String, String>>): String =
    params.joinToString("&") { (k, v) ->
        URLEncoder.encode(k, Charsets.UTF_8) + "=" + URLEncoder.encode(v, Charsets.UTF_8)
    }

fun parseQuery(query: String?): Map<String, String> =
    query
        .orEmpty()
        .split('&')
        .filter { it.contains('=') }
        .associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
        }

/** What came back to the callback address. */
data class Callback(val params: Map<String, String>)

class AuthCancelled(message: String) : Exception(message)

/**
 * A one-shot listener on 127.0.0.1 for an OAuth redirect. It accepts only [path], answers with a
 * short "you can close this tab" page and stops after the first valid callback or [timeoutMillis].
 *
 * When [fragmentBridge] is set (for implicit grants, whose token arrives in the URL fragment the
 * browser never sends), the callback page reads the fragment with a script and posts it back to
 * `path + "/fragment"`.
 */
class LoopbackReceiver(
    private val port: Int,
    private val path: String,
    private val fragmentBridge: Boolean = false,
    private val timeoutMillis: Long = 5 * 60 * 1000L,
) {
    suspend fun await(onListening: () -> Unit): Callback {
        val result = CompletableDeferred<Callback>()
        val server =
            try {
                HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
            } catch (e: java.io.IOException) {
                throw AuthCancelled(
                    "Port $port is in use, so Beako can’t receive the sign-in. Close the other program using it and try again.",
                )
            }
        server.createContext("/") { exchange ->
            val uri: URI = exchange.requestURI
            val (status, page, callback) =
                when {
                    uri.path == path && fragmentBridge && uri.rawQuery.isNullOrEmpty() ->
                        Triple(200, FRAGMENT_PAGE.replace("{{path}}", "$path/fragment"), null)
                    uri.path == path || (fragmentBridge && uri.path == "$path/fragment") ->
                        Triple(200, DONE_PAGE, Callback(parseQuery(uri.rawQuery)))
                    else -> Triple(404, "Not found", null)
                }
            val bytes = page.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.responseHeaders.add("Referrer-Policy", "no-referrer")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            if (callback != null) result.complete(callback)
        }
        server.start()
        try {
            onListening()
            return withTimeout(timeoutMillis) { result.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AuthCancelled("Sign-in timed out. Try again when you’re ready.")
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        const val DONE_PAGE =
            "<!doctype html><meta charset=utf-8><title>Beako</title>" +
                "<body style='font-family:system-ui;background:#0b0c0e;color:#f5f5f6;display:grid;place-items:center;height:90vh'>" +
                "<div><h2>You can close this tab</h2><p>Return to Beako to finish.</p></div>"

        /** Reads an implicit-grant token from the fragment and hands it to the app. */
        const val FRAGMENT_PAGE =
            "<!doctype html><meta charset=utf-8><title>Beako</title>" +
                "<script>var f=location.hash.slice(1);history.replaceState(null,'',location.pathname);" +
                "location.replace('{{path}}?'+f);</script>"
    }
}
