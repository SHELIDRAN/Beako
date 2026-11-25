package org.nekomanga.shared.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.BeakoJson
import org.nekomanga.shared.net.HttpFetcher

/** A published Beako release with a Windows installer. */
data class AppRelease(
    /** "0.1.8", from the tag `beako-v0.1.8`. */
    val version: String,
    /** The release notes, as written on GitHub (Markdown). */
    val notes: String,
    /** The release's page on GitHub. */
    val pageUrl: String,
    val installerUrl: String,
    val installerName: String,
    val installerSize: Long,
    /** The `.sha256` file published beside the installer, if any. */
    val checksumUrl: String?,
)

@Serializable
private data class ReleaseDto(
    val tag_name: String = "",
    val body: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
)

/**
 * Beako's own updates, from the GitHub Releases of [repository]. Checks the latest release, and
 * downloads its `.msi` to a folder, checking the size and the published SHA-256 before handing it
 * over. Downloads come only from GitHub's hosts.
 */
class AppUpdates(
    private val http: HttpFetcher,
    private val client: OkHttpClient,
    private val repository: String = REPOSITORY,
    private val api: String = "https://api.github.com",
) {
    /** The newest published release, or null when there is none with a Windows installer. */
    suspend fun latest(): AppRelease? {
        val response = http.get("$api/repos/$repository/releases/latest")
        if (response.code == 404) return null
        if (response.code == 403 || response.code == 429) throw ApiError.RateLimited(SERVICE, null)
        if (response.code !in 200..299) throw ApiError.ServerError(SERVICE, response.code)
        val release =
            try {
                BeakoJson.decodeFromString(ReleaseDto.serializer(), response.body)
            } catch (e: SerializationException) {
                throw ApiError.Unreadable(SERVICE, e)
            }
        return parse(release)
    }

    /**
     * Downloads [release]'s installer into [folder], calling [progress] with 0..1 as it goes, and
     * returns the file once its size and checksum match. A partial or mismatched file is deleted.
     */
    suspend fun download(
        release: AppRelease,
        folder: File,
        progress: (Float) -> Unit = {},
    ): File =
        withContext(Dispatchers.IO) {
            val expected =
                checksum(
                    release.checksumUrl
                        ?: throw IOException(
                            "This release has no checksum to check the installer against. Download it from GitHub instead.",
                        ),
                    release.installerName,
                )
            folder.mkdirs()
            // Earlier downloads are no longer needed.
            folder
                .listFiles { f -> f.name.endsWith(".msi") || f.name.endsWith(".part") }
                ?.forEach { it.delete() }
            val target = File(folder, safeName(release.installerName))
            val part = File(folder, target.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val call = client.newCall(Request.Builder().url(trusted(release.installerUrl)).build())
            // A blocked read doesn't see coroutine cancellation; cancelling the call ends it.
            val stop = coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { response ->
                    trusted(response.request.url.toString())
                    if (!response.isSuccessful)
                        throw IOException("GitHub answered ${response.code}.")
                    val body = response.body
                    val total = release.installerSize.takeIf { it > 0 } ?: body.contentLength()
                    if (total > MAX_BYTES)
                        throw IOException("The installer is larger than expected.")
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                read += n
                                if (read > MAX_BYTES)
                                    throw IOException("The installer is larger than expected.")
                                output.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                if (total > 0) progress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                            if (release.installerSize > 0 && read != release.installerSize)
                                throw IOException("The download was cut short. Try again.")
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true))
                    throw IOException("The download doesn’t match its checksum. Try again.")
                target.delete()
                if (!part.renameTo(target)) throw IOException("Couldn’t save the installer.")
                progress(1f)
                target
            } catch (e: Throwable) {
                part.delete()
                throw e
            } finally {
                stop?.dispose()
            }
        }

    private suspend fun checksum(url: String, installer: String): String {
        val response = http.get(trusted(url))
        if (response.code !in 200..299)
            throw IOException("Couldn’t get the installer’s checksum (${response.code}).")
        // `sha256sum` format: "<hex>  <file name>".
        val line =
            response.body
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && (it.endsWith(installer) || !it.contains(' ')) }
                ?: throw IOException("The installer’s checksum file is empty.")
        val hex = line.substringBefore(' ').lowercase()
        if (!hex.matches(Regex("[0-9a-f]{64}")))
            throw IOException("The installer’s checksum file isn’t readable.")
        return hex
    }

    private fun parse(release: ReleaseDto): AppRelease? {
        if (release.draft || release.prerelease) return null
        val version = versionOf(release.tag_name) ?: return null
        val installer =
            release.assets.firstOrNull {
                it.name.endsWith(".msi", ignoreCase = true) && it.name.contains("windows", true)
            }
                ?: release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
                ?: return null
        val checksum = release.assets.firstOrNull { it.name == installer.name + ".sha256" }
        return AppRelease(
            version = version,
            notes = release.body.orEmpty().trim(),
            pageUrl = release.html_url,
            installerUrl = installer.browser_download_url,
            installerName = installer.name,
            installerSize = installer.size,
            checksumUrl = checksum?.browser_download_url,
        )
    }

    companion object {
        const val SERVICE = "GitHub"
        const val REPOSITORY = "SHELIDRAN/Beako"
        private const val MAX_BYTES = 600L * 1024 * 1024
        private val TRUSTED_HOSTS =
            setOf(
                "github.com",
                "objects.githubusercontent.com",
                "release-assets.githubusercontent.com",
            )

        /** "0.1.8" from `beako-v0.1.8` or `v0.1.8`; null for anything else. */
        fun versionOf(tag: String): String? =
            Regex("""^(?:beako-)?v?(\d+(?:\.\d+){1,3})$""").find(tag.trim())?.groupValues?.get(1)

        /** Whether [candidate] is a later version than [installed]; a "dev" build never updates. */
        fun isNewer(candidate: String, installed: String): Boolean {
            if (versionOf(installed) == null) return false
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** Installers come only over https from GitHub. */
        internal fun trusted(url: String): String {
            val parsed = url.toHttpUrlOrNull() ?: throw IOException("Not a download link.")
            if (!parsed.isHttps || parsed.host !in TRUSTED_HOSTS)
                throw IOException("Updates download only from GitHub.")
            return url
        }

        private fun safeName(name: String) =
            name.replace(Regex("""[^A-Za-z0-9._-]"""), "_").takeLast(120).ifBlank { "Beako.msi" }
    }
}
