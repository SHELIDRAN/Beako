package org.nekomanga.desktop.state

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.nekomanga.desktop.platform.WindowsVault
import org.nekomanga.shared.auth.SecretVault
import org.nekomanga.shared.comick.ComickApi
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.download.DownloadManager
import org.nekomanga.shared.download.ImageFetcher
import org.nekomanga.shared.library.AppDirs
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.mangadex.MangaDexAccount
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.net.OkHttpFetcher
import org.nekomanga.shared.net.OkHttpTransport
import org.nekomanga.shared.net.beakoUserAgent
import org.nekomanga.shared.tracking.anilist.AniListTracker
import org.nekomanga.shared.tracking.comick.ComickAccount
import org.nekomanga.shared.tracking.comick.ComickTracker

/** The app's long-lived services, created once at start-up. */
class AppContainer(
    version: String = System.getProperty("beako.version") ?: "dev",
    val dirs: AppDirs = AppDirs(),
    /** Where sign-in secrets are kept: Credential Manager on Windows. */
    val vault: SecretVault = WindowsVault.forThisSystem(),
) {
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
    val library = LibraryStore(dirs.libraryFile)
    val settings = SettingsStore(dirs.settingsFile)

    private val transport = OkHttpTransport(httpClient, userAgent)
    val comickAccount = ComickAccount(transport, vault)
    val comickTracker = ComickTracker(comickAccount)
    val aniList = AniListTracker(transport, vault)
    val mangaDexAccount = MangaDexAccount(transport, vault)

    /** Runs work that outlives any one screen, such as downloads. */
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads =
        DownloadManager(
            root = dirs.downloads,
            scope = backgroundScope,
            pageList = { chapter -> mangaDex.pages(chapter.id, settings.current.dataSaver) },
            images = ImageFetcher { url -> fetchBytes(url) },
        )

    private suspend fun fetchBytes(url: String): ByteArray {
        val call = httpClient.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) =
                        continuation.resumeWithException(e)

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!it.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("Image server returned ${it.code}"),
                                )
                            } else {
                                try {
                                    continuation.resume(it.body.bytes())
                                } catch (e: IOException) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
