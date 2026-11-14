package org.nekomanga.shared.extensions

import java.net.InetAddress
import java.net.URI

/**
 * What extension code may reach. Extensions are third-party code: they get HTTP(S) to public hosts
 * only, never this computer or its local network (which would expose Beako's sign-in callback and
 * home devices), and never Comick, which Beako uses for discovery and tracking only.
 */
object HostPolicy {

    class Blocked(message: String) : Exception(message)

    /** @throws Blocked when [url] isn't allowed. */
    fun check(url: String) {
        val uri = runCatching { URI(url) }.getOrNull() ?: throw Blocked("Not a web address: $url")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") throw Blocked("Only web addresses are allowed")
        val host = uri.host?.lowercase()?.trimEnd('.') ?: throw Blocked("No host in $url")
        if (isComick(host)) throw Blocked("Extensions can’t read from Comick")
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw Blocked("Extensions can’t reach this computer")
        }
        literalAddress(host)?.let {
            if (isPrivate(it)) throw Blocked("Extensions can’t reach the local network")
        }
    }

    /** True for addresses an extension must never connect to, after DNS resolution too. */
    fun isPrivate(address: InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            // Unique local IPv6 (fc00::/7) and carrier-grade NAT (100.64.0.0/10).
            (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc) ||
            (address.address.size == 4 &&
                address.address[0].toInt() and 0xff == 100 &&
                (address.address[1].toInt() and 0xc0) == 64)

    fun isComick(host: String): Boolean = host.split('.').any { it.contains("comick") }

    private fun literalAddress(host: String): InetAddress? {
        val bare = host.removePrefix("[").removeSuffix("]")
        val looksLiteral = bare.contains(':') || bare.all { it.isDigit() || it == '.' }
        return if (looksLiteral) runCatching { InetAddress.getByName(bare) }.getOrNull() else null
    }
}
