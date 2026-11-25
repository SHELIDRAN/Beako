package org.nekomanga.shared

import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.nekomanga.shared.net.HttpResponse
import org.nekomanga.shared.update.AppUpdates

class AppUpdatesTest {
    private val installer = ByteArray(200_000) { (it % 251).toByte() }
    private val hash =
        MessageDigest.getInstance("SHA-256").digest(installer).joinToString("") {
            "%02x".format(it)
        }
    private val base = "https://github.com/SHELIDRAN/Beako/releases/download/beako-v0.1.9"

    private fun release(
        tag: String = "beako-v0.1.9",
        prerelease: Boolean = false,
        size: Int = installer.size,
    ) =
        """{"tag_name":"$tag","html_url":"https://github.com/SHELIDRAN/Beako/releases/tag/$tag",
            "draft":false,"prerelease":$prerelease,"body":"## New\n- **Updates** in the app",
            "assets":[{"name":"Beako-0.1.9-windows-x64.msi","size":$size,"browser_download_url":"$base/Beako-0.1.9-windows-x64.msi"},
                      {"name":"Beako-0.1.9-windows-x64.msi.sha256","size":94,"browser_download_url":"$base/Beako-0.1.9-windows-x64.msi.sha256"}]}"""

    private fun api(release: String, checksum: String = "$hash  Beako-0.1.9-windows-x64.msi\n") =
        FakeHttp { url ->
            when {
                url.endsWith("/releases/latest") -> json(release)
                url.endsWith(".sha256") -> HttpResponse(200, "application/octet-stream", checksum)
                else -> HttpResponse(404, null, "")
            }
        }

    /** Serves the installer bytes for any request, as GitHub's download host would. */
    private fun client(bytes: ByteArray = installer) =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                },
            )
            .build()

    @Test
    fun theLatestReleaseIsReadWithItsInstallerAndChecksum() {
        runBlocking {
            val latest = AppUpdates(api(release()), client()).latest()!!
            assertEquals("0.1.9", latest.version)
            assertEquals("Beako-0.1.9-windows-x64.msi", latest.installerName)
            assertTrue(latest.checksumUrl!!.endsWith(".sha256"))
            assertNull(AppUpdates(api(release(prerelease = true)), client()).latest())
            assertNull(AppUpdates(api(release(tag = "nightly")), client()).latest())
        }
    }

    @Test
    fun versionsCompareByNumberAndDevBuildsNeverUpdate() {
        assertTrue(AppUpdates.isNewer("0.1.10", "0.1.9"))
        assertTrue(AppUpdates.isNewer("0.2", "0.1.9"))
        assertFalse(AppUpdates.isNewer("0.1.8", "0.1.8"))
        assertFalse(AppUpdates.isNewer("0.1.7", "0.1.8"))
        assertFalse(AppUpdates.isNewer("9.9.9", "dev"))
        assertEquals("0.1.8", AppUpdates.versionOf("beako-v0.1.8"))
        assertNull(AppUpdates.versionOf("desktop-v0.4.0"))
    }

    @Test
    fun downloadsAreCheckedAgainstTheirChecksum() {
        runBlocking {
            val folder = Files.createTempDirectory("beako-update").toFile()
            try {
                val latest = AppUpdates(api(release()), client()).latest()!!
                var progress = 0f
                val msi =
                    AppUpdates(api(release()), client()).download(latest, folder) { progress = it }
                assertEquals(installer.size.toLong(), msi.length())
                assertEquals(1f, progress)

                val wrong = "0".repeat(64) + "  Beako-0.1.9-windows-x64.msi"
                assertFailsWith<IOException> {
                    AppUpdates(api(release(), wrong), client()).download(latest, folder)
                }
                // A failed download leaves nothing behind.
                assertTrue(
                    folder.listFiles().orEmpty().none {
                        it.name.endsWith(".msi") || it.name.endsWith(".part")
                    },
                )

                assertFailsWith<IOException> {
                    AppUpdates(api(release()), client(installer.copyOf(1000)))
                        .download(latest, folder)
                }
            } finally {
                folder.deleteRecursively()
            }
        }
    }

    @Test
    fun installersComeOnlyFromGitHub() {
        runBlocking {
            val folder = Files.createTempDirectory("beako-update").toFile()
            try {
                val latest = AppUpdates(api(release()), client()).latest()!!
                val elsewhere = latest.copy(installerUrl = "https://example.com/Beako.msi")
                assertFailsWith<IOException> {
                    AppUpdates(api(release()), client()).download(elsewhere, folder)
                }
                val plain =
                    latest.copy(installerUrl = latest.installerUrl.replace("https:", "http:"))
                assertFailsWith<IOException> {
                    AppUpdates(api(release()), client()).download(plain, folder)
                }
            } finally {
                folder.deleteRecursively()
            }
        }
    }
}
