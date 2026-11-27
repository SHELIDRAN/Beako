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
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

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
        val url = request.url.toString()
        val start = clock()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                // A load Beako cancelled (scrolled past, reader closed) says nothing about the
                // node.
                if (!chain.call().isCanceled()) send(url, false, 0, millis(start), cached = false)
                throw e
            }
        val cached = response.header("X-Cache").orEmpty().startsWith("HIT", ignoreCase = true)
        if (!response.isSuccessful) {
            send(url, false, 0, millis(start), cached)
            return@Interceptor response
        }
        // Reported once the whole image has arrived (or failed), timed over the full download.
        response
            .newBuilder()
            .body(
                Measured(response.body, chain.call()) { ok, bytes ->
                    send(url, ok, bytes, millis(start), cached)
                },
            )
            .build()
    }

    /**
     * Passes the image through, counting bytes, and reports once: at the end of the image, or on a
     * read error unless the call was cancelled. A body closed early (not needed after all) isn't
     * reported.
     */
    private class Measured(
        private val body: ResponseBody,
        private val call: Call,
        private val report: (success: Boolean, bytes: Long) -> Unit,
    ) : ResponseBody() {
        private var done = false
        private var bytes = 0L

        override fun contentType() = body.contentType()

        override fun contentLength() = body.contentLength()

        private val counting =
            object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val n =
                        try {
                            super.read(sink, byteCount)
                        } catch (e: IOException) {
                            finish(success = false)
                            throw e
                        }
                    if (n == -1L) finish(success = true) else bytes += n
                    return n
                }
            }
        private val buffered = counting.buffer()

        private fun finish(success: Boolean) {
            if (done) return
            done = true
            if (success || !call.isCanceled()) report(success, bytes)
        }

        override fun source(): BufferedSource = buffered
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
