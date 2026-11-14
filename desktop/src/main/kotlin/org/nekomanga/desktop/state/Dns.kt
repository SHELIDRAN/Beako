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

/**
 * Resolves names with the DNS-over-HTTPS provider chosen in settings, or the system's DNS when none
 * is. Reads the setting on every lookup, so changing it applies without a restart.
 */
class SwitchableDns(private val provider: () -> String, private val bootstrapClient: OkHttpClient) :
    Dns {
    private val resolvers = mutableMapOf<DohProvider, Dns>()

    override fun lookup(hostname: String): List<InetAddress> {
        val choice = DohProvider.of(provider()) ?: return Dns.SYSTEM.lookup(hostname)
        return resolver(choice).lookup(hostname)
    }

    @Synchronized
    private fun resolver(choice: DohProvider): Dns =
        resolvers.getOrPut(choice) {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(choice.url.toHttpUrl())
                .bootstrapDnsHosts(choice.bootstrap.map { InetAddress.getByName(it) })
                .includeIPv6(true)
                .build()
        }
}
