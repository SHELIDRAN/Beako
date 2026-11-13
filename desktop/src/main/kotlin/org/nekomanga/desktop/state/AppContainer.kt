package org.nekomanga.desktop.state

import okhttp3.OkHttpClient
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.net.OkHttpFetcher
import org.nekomanga.shared.net.beakoUserAgent

/** The app's long-lived services, created once at start-up. */
class AppContainer(version: String = System.getProperty("beako.version") ?: "dev") {
    val userAgent = beakoUserAgent(version)

    /** Shared by API calls and image loading; every request names Beako. */
    val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    if (request.header("User-Agent") != null) request
                    else request.newBuilder().header("User-Agent", userAgent).build(),
                )
            }
            .build()

    private val http = OkHttpFetcher(httpClient, userAgent)
    val mangaDex = MangaDexApi(http)
    val comick = ComickApi(http)
    val discovery = Discovery(comick, mangaDex)
}
