package org.nekomanga.shared.net

import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Any HTTP request: method, URL, headers and an optional body. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
) {
    override fun toString() = "$method $url" // Never print headers or bodies: they hold tokens.

    companion object {
        fun form(
            url: String,
            fields: List<Pair<String, String>>,
            headers: Map<String, String> = emptyMap(),
        ) =
            HttpRequest(
                "POST",
                url,
                headers,
                fields.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" },
                "application/x-www-form-urlencoded",
            )

        fun json(
            method: String,
            url: String,
            body: String?,
            headers: Map<String, String> = emptyMap(),
        ) = HttpRequest(method, url, headers, body, if (body != null) "application/json" else null)

        private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)
    }
}

/** Sends [HttpRequest]s. Kept small so API clients can be tested without a network. */
fun interface HttpTransport {
    /** @throws IOException when the server can't be reached. */
    suspend fun send(request: HttpRequest): HttpResponse
}

class OkHttpTransport(private val client: OkHttpClient, private val userAgent: String) :
    HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val body = request.body?.toRequestBody((request.contentType ?: "text/plain").toMediaType())
        val builder =
            Request.Builder()
                .url(request.url)
                .method(
                    request.method,
                    body
                        ?: if (request.method in BODY_METHODS) ByteArray(0).toRequestBody()
                        else null,
                )
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val call = client.newCall(builder.build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) =
                        continuation.resumeWithException(e)

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { r ->
                                val headers =
                                    r.headers.names().associate {
                                        it.lowercase() to r.headers[it].orEmpty()
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

    private companion object {
        val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    }
}

/** Lets a transport stand in where only GET is needed. */
fun HttpTransport.asFetcher(): HttpFetcher = HttpFetcher { url -> send(HttpRequest("GET", url)) }

/**
 * Sends [request] and returns the JSON response, turning failures into [ApiError]s the same way
 * [getJson] does. 401 and 403 become [ApiError.Unauthorized] so callers can refresh or reconnect.
 * An empty successful body (e.g. 204) returns null.
 */
suspend fun HttpTransport.sendJson(service: String, request: HttpRequest): JsonElement? {
    val response =
        try {
            send(request)
        } catch (e: IOException) {
            throw ApiError.Unreachable(service, e)
        }
    if (response.code == 401 || response.code == 403) {
        throw ApiError.Unauthorized(service, response.code, errorCode(response))
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
            val code = errorCode(response)
            throw ApiError.Rejected(service, response.code, code?.let { ": $it" }.orEmpty(), code)
        }
    }
    if (response.body.isBlank()) return null
    return try {
        BeakoJson.parseToJsonElement(response.body)
    } catch (e: SerializationException) {
        throw ApiError.Unreadable(service, e)
    }
}

/** The OAuth `error` code or API `message` of an error response, never the whole body. */
private fun errorCode(response: HttpResponse): String? = runCatching {
    val json = BeakoJson.parseToJsonElement(response.body)
    json.str("error") ?: json.str("message")
}
    .getOrNull()
    ?.take(120)
