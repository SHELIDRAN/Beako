package org.nekomanga.shared

import java.io.IOException
import org.nekomanga.shared.net.HttpFetcher
import org.nekomanga.shared.net.HttpResponse

fun fixture(path: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("fixtures/$path")) {
            "Missing fixture $path"
        }
        .use { it.readBytes().decodeToString() }

fun json(body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) =
    HttpResponse(code, "application/json; charset=utf-8", body, headers)

/** Answers each request with [respond] and records the URLs asked for. */
class FakeHttp(private val respond: (String) -> HttpResponse) : HttpFetcher {
    val urls = mutableListOf<String>()

    override suspend fun get(url: String): HttpResponse {
        urls += url
        return respond(url)
    }
}

val offline = FakeHttp { throw IOException("no network") }

val cloudflare = FakeHttp {
    HttpResponse(403, "text/html; charset=UTF-8", "<!DOCTYPE html><title>Just a moment...</title>")
}
