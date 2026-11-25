package org.nekomanga.shared.mangadex

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * MangaDex@Home load reports. MangaDex asks every app that loads pages from its volunteer image
 * servers (`*.mangadex.network`) to report each load: the page's URL, whether it worked, its size,
 * how long it took and whether the server had it cached. MangaDex uses these to take slow or broken
 * servers out of rotation. Images from MangaDex's own servers (`*.mangadex.org`) are not reported,
 * and nothing about the user is sent.
 *
 * Add [interceptor] to the client that loads pages; reports go out in the background through
 * [reporter], a client without it.
 */
class AtHomeReports(
    reporter: OkHttpClient,
    private val reportUrl: String = REPORT_URL,
    private val clock: () -> Long = System::nanoTime,
) {
    private val client = reporter.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        if (!isAtHomePage(request.url)) return@Interceptor chain.proceed(request)
        val start = clock()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                send(
                    request.url.toString(),
                    success = false,
                    bytes = 0,
                    millis(start),
                    cached = false,
                )
                throw e
            }
        send(
            request.url.toString(),
            success = response.isSuccessful,
            bytes = response.body.contentLength().coerceAtLeast(0),
            duration = millis(start),
            cached = response.header("X-Cache").orEmpty().startsWith("HIT", ignoreCase = true),
        )
        response
    }

    private fun millis(start: Long) = TimeUnit.NANOSECONDS.toMillis(clock() - start)

    private fun send(url: String, success: Boolean, bytes: Long, duration: Long, cached: Boolean) {
        val body = buildJsonObject {
            put("url", url)
            put("success", success)
            put("bytes", bytes)
            put("duration", duration)
            put("cached", cached)
        }
            .toString()
        val call =
            client.newCall(
                Request.Builder()
                    .url(reportUrl)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build(),
            )
        // A report that can't be sent is dropped: it must never hold up or break reading.
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = Unit

                override fun onResponse(call: Call, response: Response) = response.close()
            },
        )
    }

    companion object {
        const val REPORT_URL = "https://api.mangadex.network/report"

        /** A page served by a MangaDex@Home node (not MangaDex's own servers or the report API). */
        fun isAtHomePage(url: HttpUrl): Boolean {
            val host = url.host.lowercase()
            if (!host.endsWith(".mangadex.network") || host == "api.mangadex.network") return false
            return url.pathSegments.any { it == "data" || it == "data-saver" }
        }
    }
}
