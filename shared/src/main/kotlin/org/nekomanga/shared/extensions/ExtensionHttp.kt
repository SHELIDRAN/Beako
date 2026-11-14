package org.nekomanga.shared.extensions

import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.nekomanga.shared.net.RateLimiter

/**
 * Extension HTTP: OkHttp limited by [HostPolicy] on every hop (redirects included) and after DNS,
 * so a script can't reach this computer, the local network or Comick. Cookies are kept in memory
 * per site, like a browser session.
 */
class ExtensionHttp(base: OkHttpClient, private val userAgent: String) : ExtensionTransport {
    private val limiter = RateLimiter(permits = 4, periodMillis = 1_000)

    // Resolves through the app's DNS (DNS over HTTPS when chosen), then drops private addresses.
    private val upstream = base.dns
    private val publicDns = Dns { host ->
        val addresses = upstream.lookup(host).filterNot(HostPolicy::isPrivate)
        addresses.ifEmpty { throw UnknownHostException("$host isn’t a public address") }
    }

    private val cookies = ConcurrentHashMap<String, List<Cookie>>()
    private val jar =
        object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val kept =
                    this@ExtensionHttp.cookies[url.host].orEmpty().filterNot { old ->
                        cookies.any { it.name == old.name }
                    }
                this@ExtensionHttp.cookies[url.host] = (kept + cookies).takeLast(50)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookies[url.host].orEmpty().filter {
                    it.matches(url) && it.expiresAt > System.currentTimeMillis()
                }
        }

    private val client =
        base
            .newBuilder()
            .dns(publicDns)
            .cookieJar(jar)
            .callTimeout(60, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                // Checked again for every redirect hop.
                try {
                    HostPolicy.check(chain.request().url.toString())
                } catch (e: HostPolicy.Blocked) {
                    throw IOException(e.message)
                }
                chain.proceed(chain.request())
            }
            .build()

    override suspend fun send(request: ExtensionRequest): ExtensionResponse {
        // Scripts often build URLs without encodeURIComponent ("?q=solo leveling").
        val url =
            normaliseUrl(request.url) ?: throw IOException("Not a web address: ${request.url}")
        try {
            HostPolicy.check(url)
        } catch (e: HostPolicy.Blocked) {
            throw IOException(e.message)
        }
        limiter.acquire()
        val builder = Request.Builder().url(url)
        request.headers.forEach { (name, value) -> runCatching { builder.header(name, value) } }
        if (request.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", userAgent)
        }
        val contentType =
            request.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
        val body =
            when (val b = request.body) {
                null -> null
                is JsonPrimitive ->
                    (b.contentOrNull ?: "").toRequestBody(contentType?.toMediaTypeOrNull())
                is JsonObject ->
                    if (contentType?.contains("json") == true)
                        b.toString().toRequestBody(contentType.toMediaTypeOrNull())
                    else
                        FormBody.Builder()
                            .apply {
                                b.forEach { (k, v) ->
                                    add(k, (v as? JsonPrimitive)?.contentOrNull ?: v.toString())
                                }
                            }
                            .build()
                is JsonArray ->
                    b.toString()
                        .toRequestBody((contentType ?: "application/json").toMediaTypeOrNull())
            }
        val method = request.method.uppercase()
        builder.method(
            method,
            if (method == "GET" || method == "HEAD") null else body ?: ByteArray(0).toRequestBody(),
        )
        val call =
            client
                .newBuilder()
                .followRedirects(request.followRedirects)
                .followSslRedirects(request.followRedirects)
                .build()
                .newCall(builder.build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) =
                        continuation.resumeWithException(e)

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            try {
                                val source = it.body.source()
                                if (source.request(MAX_BODY + 1)) {
                                    throw IOException("The page is too large")
                                }
                                val text = source.buffer.readUtf8()
                                continuation.resume(
                                    ExtensionResponse(
                                        it.code,
                                        text,
                                        it.headers.toMultimap().mapValues { (_, v) ->
                                            v.joinToString(", ")
                                        },
                                        it.request.url.toString(),
                                    ),
                                )
                            } catch (e: IOException) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                },
            )
        }
    }

    companion object {
        /**
         * [url] with spaces and other characters a URL can't hold percent-encoded, as browsers do;
         * already-encoded parts stay as they are. Null when it isn't an http(s) address.
         */
        fun normaliseUrl(url: String): String? {
            val trimmed = url.trim()
            trimmed.toHttpUrlOrNull()?.let {
                return it.toString()
            }
            val escaped = buildString {
                for (c in trimmed) {
                    when {
                        c == ' ' -> append("%20")
                        c.code < 0x20 || c in "\"<>\\^`{|}" -> append("%%%02X".format(c.code))
                        else -> append(c)
                    }
                }
            }
            return escaped.toHttpUrlOrNull()?.toString()
        }

        /** Larger answers fail inside the source instead of exhausting the app's memory. */
        const val MAX_BODY = 32L * 1024 * 1024

        /** A browser-like agent that still names Beako, as many sites refuse unknown clients. */
        fun userAgent(version: String) =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 Beako/$version"
    }
}
