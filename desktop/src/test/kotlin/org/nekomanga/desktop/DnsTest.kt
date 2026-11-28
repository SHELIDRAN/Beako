package org.nekomanga.desktop

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.Dns
import org.nekomanga.desktop.state.DnsMode
import org.nekomanga.desktop.state.DohProvider
import org.nekomanga.desktop.state.SwitchableDns

class DnsTest {

    private val poisoned =
        listOf(InetAddress.getByName("10.10.34.35")) // what a blocking network answers
    private val real = listOf(InetAddress.getByName("104.21.0.1"))

    private class Counting(private val answer: () -> List<InetAddress>) : Dns {
        var calls = 0

        override fun lookup(hostname: String): List<InetAddress> {
            calls++
            return answer()
        }
    }

    @Test
    fun automaticMergesBothSourcesSoTheConnectionCanRaceThem() {
        val system = Counting { poisoned }
        val cloudflare = Counting { real }
        val dns = SwitchableDns({ DnsMode.AUTOMATIC }, system, { 0L }) { cloudflare }

        // Over-HTTPS answers first, then Windows DNS's: one of them connects whichever is blocked.
        assertEquals(real + poisoned, dns.lookup("comick.dev"))
        assertEquals(real + poisoned, dns.lookup("comick.dev"))
        assertEquals(1, cloudflare.calls, "the answer is cached")
        assertEquals(1, system.calls)
    }

    @Test
    fun aBlockedWindowsDnsStillResolvesOverHttps() {
        val system = Counting { throw UnknownHostException("blocked") }
        val dns = SwitchableDns({ DnsMode.AUTOMATIC }, system, { 0L }) { Counting { real } }
        assertEquals(real, dns.lookup("comix.to"))
    }

    @Test
    fun whenDnsOverHttpsIsBlockedItFallsBackAndStopsTryingForAWhile() {
        var now = 0L
        val system = Counting { real }
        val blocked = Counting { throw UnknownHostException("cloudflare-dns.com unreachable") }
        val dns = SwitchableDns({ DnsMode.AUTOMATIC }, system, { now }) { blocked }

        assertEquals(real, dns.lookup("a.test"))
        val triesAfterFirst = blocked.calls
        assertEquals(2, triesAfterFirst, "Cloudflare, then Google")
        assertEquals(real, dns.lookup("b.test"))
        assertEquals(triesAfterFirst, blocked.calls, "skipped while it's down")

        now += 11 * 60_000
        dns.lookup("c.test")
        assertEquals(triesAfterFirst + 2, blocked.calls, "tried again later")
    }

    @Test
    fun aSlowDnsOverHttpsDoesNotHoldUpWindowsAnswer() {
        // 0.1.12: DNS over HTTPS never answered on the owner's network, so every first lookup
        // waited ~10 s for its timeouts although Windows DNS had already answered.
        val release = java.util.concurrent.CountDownLatch(1)
        val slow = Counting {
            release.await()
            real
        }
        val system = Counting { poisoned }
        val dns =
            SwitchableDns({ DnsMode.AUTOMATIC }, system, System::currentTimeMillis, 100L) { slow }

        val started = System.nanoTime()
        assertEquals(poisoned, dns.lookup("comick.dev"))
        val tookMillis = (System.nanoTime() - started) / 1_000_000
        kotlin.test.assertTrue(tookMillis < 2_000, "answered in $tookMillis ms")

        // When the over-HTTPS answer turns up later, the next lookup gets both.
        release.countDown()
        val deadline = System.currentTimeMillis() + 5_000
        while (dns.lookup("comick.dev") != real + poisoned) {
            kotlin.test.assertTrue(System.currentTimeMillis() < deadline, "late answer was kept")
            Thread.sleep(10)
        }
    }

    @Test
    fun withoutAWindowsAnswerItWaitsForDnsOverHttps() {
        val slow = Counting {
            Thread.sleep(300)
            real
        }
        val dns =
            SwitchableDns(
                { DnsMode.AUTOMATIC },
                Counting { throw UnknownHostException("blocked") },
                System::currentTimeMillis,
                50L,
            ) {
                slow
            }
        assertEquals(real, dns.lookup("comix.to"))
    }

    @Test
    fun nothingResolvesOnlyWhenBothFail() {
        val dns =
            SwitchableDns(
                { DnsMode.AUTOMATIC },
                Counting { throw UnknownHostException("nope") },
                { 0L },
            ) {
                Counting { throw UnknownHostException("nope") }
            }
        kotlin.test.assertFailsWith<UnknownHostException> { dns.lookup("x.test") }
    }

    @Test
    fun windowsOnlyAndASingleProviderDoWhatTheySay() {
        val system = Counting { poisoned }
        val quad9 = Counting { real }
        var mode = DnsMode.SYSTEM
        val providers = mutableListOf<DohProvider>()
        val dns =
            SwitchableDns({ mode }, system, { 0L }) {
                providers += it
                quad9
            }

        assertEquals(poisoned, dns.lookup("x.test"))
        mode = DohProvider.QUAD9.name
        assertEquals(real, dns.lookup("x.test"))
        assertEquals(listOf(DohProvider.QUAD9), providers)
    }
}
