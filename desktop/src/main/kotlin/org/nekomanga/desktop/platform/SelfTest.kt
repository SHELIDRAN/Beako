package org.nekomanga.desktop.platform

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.net.ssl.SSLContext
import kotlinx.coroutines.runBlocking
import org.nekomanga.shared.extensions.ExtensionInfo
import org.nekomanga.shared.extensions.ExtensionResponse
import org.nekomanga.shared.extensions.ExtensionTransport
import org.nekomanga.shared.extensions.JsSource

/**
 * Checks that the runtime Beako runs on has everything it needs. The installer bundles its own Java
 * runtime, so a module missing there only shows up on users' PCs: CI runs the packaged app with
 * `--self-test <report>` before publishing a release.
 */
object SelfTest {

    /** Each check's name and its failure, or null when it passed. */
    fun run(): List<Pair<String, Throwable?>> = CHECKS.map { (name, check) ->
        name to
            try {
                check()
                null
            } catch (e: Throwable) {
                // Errors too: a missing module is a NoClassDefFoundError.
                e
            }
    }

    /** Runs the checks, writes a report to [report] and returns the process exit code. */
    fun runAndReport(report: File?): Int {
        val results = run()
        val text =
            results.joinToString("\n") { (name, error) ->
                if (error == null) "PASS $name"
                else "FAIL $name: ${error::class.qualifiedName}: ${error.message}"
            }
        report?.let { runCatching { it.writeText(text + "\n") } }
        println(text)
        return if (results.all { it.second == null }) 0 else 1
    }

    private val CHECKS: List<Pair<String, () -> Unit>> =
        listOf(
            "sign-in callback listener (jdk.httpserver)" to
                {
                    val server =
                        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
                    server.start()
                    server.stop(0)
                },
            "TLS and crypto (jdk.crypto.ec, AES)" to
                {
                    SSLContext.getInstance("TLSv1.3")
                    Cipher.getInstance("AES/GCM/NoPadding")
                    Cipher.getInstance("AES/CBC/PKCS5Padding")
                },
            "page charsets (jdk.charsets)" to
                {
                    check(
                        Charset.isSupported("Shift_JIS") &&
                            Charset.isSupported("EUC-KR") &&
                            Charset.isSupported("GBK"),
                    )
                },
            "date locales (jdk.localedata)" to
                {
                    SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).parse("2 janvier 2026")
                },
            "DNS over HTTPS (okhttp-dnsoverhttps)" to
                {
                    Class.forName("okhttp3.dnsoverhttps.DnsOverHttps")
                },
            "extension engine (QuickJS, jsoup, host API)" to
                {
                    runBlocking {
                        val code =
                            """
                        class DefaultExtension extends MProvider {
                          async getPopular(page) {
                            const res = await new Client().get("https://selftest.invalid/");
                            const doc = new Document(res.body);
                            return { list: [{ name: doc.selectFirst("p").text, link: "/x" }], hasNextPage: false };
                          }
                        }
                        """
                        val transport = ExtensionTransport {
                            ExtensionResponse(200, "<p>ok</p>", emptyMap(), it.url)
                        }
                        JsSource(
                                ExtensionInfo(id = 0, name = "Self-test", sourceCodeLanguage = 1),
                                code,
                                transport,
                            )
                            .use { check(it.popular(1).titles.single().name == "ok") }
                    }
                },
            "Windows Credential Manager (JNA)" to
                {
                    if (WindowsVault.isWindows) WindowsVault().get("selftest.${System.nanoTime()}")
                },
        )
}
