package org.nekomanga.desktop.state

import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/** DNS-over-HTTPS providers, with addresses to reach them without a DNS lookup. */
enum class DohProvider(val label: String, val url: String, val bootstrap: List<String>) {
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112"));

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name }
    }
}

/** The DNS setting's special values; anything else names a [DohProvider]. */
object DnsMode {
    /** Windows DNS and DNS over HTTPS together; connections race the addresses (the default). */
    const val AUTOMATIC = ""
    const val SYSTEM = "SYSTEM"
}

/**
 * Resolves names as the DNS setting says: [DnsMode.AUTOMATIC] (DNS over HTTPS through Cloudflare,
 * then Google, falling back to the system's DNS when neither answers), [DnsMode.SYSTEM], or one
 * provider. Reads the setting on every lookup, so a change applies without a restart.
 *
 * Automatic is the default because some networks block sites by answering their DNS lookups with an
 * address that never answers ("Connect timed out" in 0.1.1 QA), which browsers' secure DNS gets
 * around. Answers are cached briefly, and when DNS over HTTPS itself is unreachable it's skipped
 * for a while so lookups don't keep waiting on it.
 */
class SwitchableDns(
    private val mode: () -> String,
    private val system: Dns = Dns.SYSTEM,
    private val now: () -> Long = System::currentTimeMillis,
    private val doh: (DohProvider) -> Dns,
) : Dns {
    constructor(
        mode: () -> String,
        bootstrapClient: OkHttpClient,
    ) : this(mode, Dns.SYSTEM, System::currentTimeMillis, dohFactory(bootstrapClient))

    private val resolvers = java.util.concurrent.ConcurrentHashMap<DohProvider, Dns>()
    private val cache =
        java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    @Volatile private var dohDownUntil = 0L

    override fun lookup(hostname: String): List<InetAddress> {
        val setting = mode()
        DohProvider.of(setting)?.let {
            return resolver(it).lookup(hostname)
        }
        if (setting == DnsMode.SYSTEM) return system.lookup(hostname)
        cache[hostname]?.let { (at, addresses) -> if (now() - at < CACHE_MILLIS) return addresses }

        // Both sources, merged: the connection then races every address (OkHttp's fast fallback)
        // and keeps the first that answers. A network that poisons Windows DNS, and one where
        // DNS-over-HTTPS answers don't connect (0.1.2 QA: even MangaDex timed out), both work.
        val fromSystem = runCatching { system.lookup(hostname) }
        val fromDoh = if (now() < dohDownUntil) null else lookupOverHttps(hostname)
        val merged = (fromDoh.orEmpty() + fromSystem.getOrNull().orEmpty()).distinct()
        if (merged.isEmpty())
            throw fromSystem.exceptionOrNull() ?: java.net.UnknownHostException(hostname)
        if (logged.add(hostname)) {
            org.nekomanga.shared.diagnostics.DiagnosticLog.info(
                "DNS for $hostname: Windows ${fromSystem.getOrNull()?.map { it.hostAddress } ?: "failed"}, " +
                    "over HTTPS ${fromDoh?.map { it.hostAddress } ?: "unavailable"}",
            )
        }
        if (cache.size > 500) cache.clear()
        cache[hostname] = now() to merged
        return merged
    }

    /**
     * DNS over HTTPS (Cloudflare, then Google); null when neither answers, and then skipped for a
     * while.
     */
    private fun lookupOverHttps(hostname: String): List<InetAddress>? {
        for (provider in AUTOMATIC_ORDER) {
            val found = runCatching { resolver(provider).lookup(hostname) }.getOrNull()
            if (!found.isNullOrEmpty()) return found
        }
        dohDownUntil = now() + DOWN_MILLIS
        org.nekomanga.shared.diagnostics.DiagnosticLog.info(
            "DNS over HTTPS didn’t answer for $hostname; using Windows DNS alone for the next 10 minutes",
        )
        return null
    }

    private val logged =
        java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
        )

    private fun resolver(choice: DohProvider): Dns = resolvers.getOrPut(choice) { doh(choice) }

    companion object {
        private val AUTOMATIC_ORDER = listOf(DohProvider.CLOUDFLARE, DohProvider.GOOGLE)
        private const val CACHE_MILLIS = 5 * 60_000L
        private const val DOWN_MILLIS = 10 * 60_000L

        private fun dohFactory(bootstrapClient: OkHttpClient): (DohProvider) -> Dns {
            // Short timeouts, so a blocked provider falls back quickly.
            val client =
                bootstrapClient
                    .newBuilder()
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            return { choice ->
                DnsOverHttps.Builder()
                    .client(client)
                    .url(choice.url.toHttpUrl())
                    .bootstrapDnsHosts(choice.bootstrap.map { InetAddress.getByName(it) })
                    .includeIPv6(true)
                    .build()
            }
        }
    }
}
