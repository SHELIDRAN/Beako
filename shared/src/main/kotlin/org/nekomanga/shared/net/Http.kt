package org.nekomanga.shared.net

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** JSON parser shared by every API client: tolerant of fields the app doesn't know. */
val BeakoJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** The User-Agent Beako sends, e.g. `Beako/0.1.0`. */
fun beakoUserAgent(version: String): String = "Beako/$version"

data class HttpResponse(
    val code: Int,
    val contentType: String?,
    val body: String,
    /** Header names in lower case. */
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = code in 200..299

    fun header(name: String): String? = headers[name.lowercase()]
}

/** A GET request. Kept this small so API clients can be tested without a network. */
fun interface HttpFetcher {
    /** @throws IOException when the server can't be reached. */
    suspend fun get(url: String): HttpResponse
}

class OkHttpFetcher(private val client: OkHttpClient, private val userAgent: String) : HttpFetcher {
    override suspend fun get(url: String): HttpResponse {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { r ->
                                val headers =
                                    r.headers.names().associate { name ->
                                        name.lowercase() to r.headers[name].orEmpty()
                                    }
                                continuation.resume(
                                    HttpResponse(
                                        r.code,
                                        r.header("Content-Type"),
                                        r.body.string(),
                                        headers,
                                    ),
                                )
                            }
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    }
                },
            )
        }
    }
}

/** Builds `base/path?params`, repeating a parameter name for each of its values. */
fun buildUrl(base: String, path: String, params: List<Pair<String, String>> = emptyList()): String {
    val builder = base.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/'))
    for ((name, value) in params) builder.addQueryParameter(name, value)
    return builder.build().toString()
}

/** Why a request to a service failed. [service] is a display name such as "Comick". */
sealed class ApiError(val service: String, message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** The server couldn't be reached at all. */
    class Unreachable(service: String, cause: Throwable) :
        ApiError(service, "Couldn't reach $service", cause)

    /** The server answered with a web page (a Cloudflare browser check) instead of JSON. */
    class BlockedByChallenge(service: String) :
        ApiError(service, "$service is asking for a browser check right now")

    class RateLimited(service: String, val retryAfterSeconds: Long?) :
        ApiError(service, "$service is limiting requests; try again shortly")

    class ServerError(service: String, val code: Int) :
        ApiError(service, "$service had a server error ($code)")

    class Rejected(service: String, val code: Int, detail: String) :
        ApiError(service, "$service refused the request ($code)" + detail)

    class Unreadable(service: String, cause: Throwable) :
        ApiError(service, "$service sent a response Beako couldn't read", cause)

    /** True when the service itself is unavailable, as opposed to a bad request. */
    val isOutage: Boolean
        get() =
            this is Unreachable ||
                this is BlockedByChallenge ||
                this is RateLimited ||
                this is ServerError
}

/**
 * GETs [url] and returns its JSON, turning every failure into an [ApiError]. Checks the status and
 * content type before parsing, because Cloudflare can answer with an HTML challenge.
 */
suspend fun HttpFetcher.getJson(service: String, url: String): JsonElement {
    val response =
        try {
            get(url)
        } catch (e: IOException) {
            throw ApiError.Unreachable(service, e)
        }
    val looksLikeHtml =
        response.contentType?.contains("html", ignoreCase = true) == true ||
            response.body.trimStart().startsWith("<")
    if (looksLikeHtml) throw ApiError.BlockedByChallenge(service)
    when {
        response.code == 429 ->
            throw ApiError.RateLimited(service, response.header("Retry-After")?.toLongOrNull())
        response.code >= 500 -> throw ApiError.ServerError(service, response.code)
        !response.isSuccessful -> {
            val detail = runCatching {
                BeakoJson.parseToJsonElement(response.body).str("message")
            }
                .getOrNull()
                ?.let { ": $it" }
                .orEmpty()
            throw ApiError.Rejected(service, response.code, detail)
        }
    }
    return try {
        BeakoJson.parseToJsonElement(response.body)
    } catch (e: SerializationException) {
        throw ApiError.Unreadable(service, e)
    }
}
