package org.nekomanga.shared

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.nekomanga.shared.mangadex.AtHomeReports
import org.nekomanga.shared.net.BeakoJson

class AtHomeReportsTest {
    private fun answer(code: Int = 200, cache: String? = "HIT", bytes: Int = 1234) =
        Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .apply { cache?.let { header("X-Cache", it) } }
                .body(ByteArray(bytes).toResponseBody("image/jpeg".toMediaType()))
                .build()
        }

    @Test
    fun pagesFromAtHomeNodesAreReportedAndOthersAreNot() {
        val reports = CopyOnWriteArrayList<String>()
        val sent = CountDownLatch(1)
        val reporter =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    reports += chain.request().url.toString() + " " + buffer.readUtf8()
                    sent.countDown()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }
                .build()
        val atHome = AtHomeReports(reporter)
        val client =
            OkHttpClient.Builder()
                .addInterceptor(atHome.interceptor)
                .addInterceptor(answer())
                .build()

        val page = "https://abc123.xyz.mangadex.network:443/token/data/hash/1.jpg"
        // Reported once the image has been read through.
        client.newCall(Request.Builder().url(page).build()).execute().use { it.body.bytes() }
        assertTrue(sent.await(5, TimeUnit.SECONDS))
        val (url, body) = reports.single().split(" ", limit = 2)
        assertEquals(AtHomeReports.REPORT_URL, url)
        val json = BeakoJson.parseToJsonElement(body).jsonObject
        // OkHttp writes the address without the default :443; it's the same image.
        assertEquals(page.replace(":443", ""), json["url"]!!.jsonPrimitive.content)
        assertTrue(json["success"]!!.jsonPrimitive.boolean)
        assertEquals(1234, json["bytes"]!!.jsonPrimitive.long)
        assertTrue(json["cached"]!!.jsonPrimitive.boolean)

        // A server error is a failure; an image closed early (not needed) isn't reported.
        val failing =
            OkHttpClient.Builder()
                .addInterceptor(atHome.interceptor)
                .addInterceptor(answer(code = 502))
                .build()
        val more = CountDownLatch(1)
        reports.clear()
        Thread {
            while (reports.isEmpty()) Thread.sleep(10)
            more.countDown()
        }
            .start()
        failing.newCall(Request.Builder().url(page).build()).execute().close()
        assertTrue(more.await(5, TimeUnit.SECONDS))
        assertTrue("\"success\":false" in reports.single())
        reports.clear()
        client.newCall(Request.Builder().url(page).build()).execute().close()
        Thread.sleep(300)
        assertTrue(reports.isEmpty())

        // MangaDex's own servers, covers and the API are not reported.
        assertFalse(
            AtHomeReports.isAtHomePage("https://uploads.mangadex.org/data/hash/1.jpg".toHttpUrl()),
        )
        assertFalse(AtHomeReports.isAtHomePage("https://api.mangadex.network/report".toHttpUrl()))
        assertFalse(
            AtHomeReports.isAtHomePage("https://uploads.mangadex.org/covers/x/y.jpg".toHttpUrl()),
        )
        assertTrue(
            AtHomeReports.isAtHomePage(
                "https://n.mangadex.network/t/data-saver/h/1.jpg".toHttpUrl(),
            ),
        )
    }

    @Test
    fun imagesPassThroughByteForByte() {
        val image = kotlin.random.Random(42).nextBytes(3_000_000)
        val reporter =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }
                .build()
        val client =
            OkHttpClient.Builder()
                .addInterceptor(AtHomeReports(reporter).interceptor)
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(image.toResponseBody("image/jpeg".toMediaType()))
                        .build()
                }
                .build()
        val page = "https://abc.xyz.mangadex.network/token/data/hash/1.jpg"
        // Read in small, uneven pieces, as a decoder might.
        val read =
            client.newCall(Request.Builder().url(page).build()).execute().use { response ->
                val source = response.body.source()
                val out = okio.Buffer()
                var step = 1L
                while (true) {
                    val n = source.read(out, step)
                    if (n == -1L) break
                    step = (step * 7 + 3) % 20_000 + 1
                }
                out.readByteArray()
            }
        assertTrue(image.contentEquals(read), "the image must arrive unchanged")
        // And through the usual whole-body read.
        val whole =
            client.newCall(Request.Builder().url(page).build()).execute().use { it.body.bytes() }
        assertTrue(image.contentEquals(whole))
    }
}
