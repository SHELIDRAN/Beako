package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.nekomanga.desktop.platform.Activity
import org.nekomanga.desktop.platform.DiscordPipe
import org.nekomanga.desktop.platform.DiscordPresence

class DiscordPresenceTest {

    private class FakePipe : DiscordPipe {
        val sent = mutableListOf<Pair<Int, String>>()

        override fun write(opcode: Int, json: String) {
            sent += opcode to json
        }

        override fun read() = DiscordPresence.OP_FRAME to """{"evt":"READY"}"""

        override fun close() {}
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun handshakesOnceThenSetsAndClearsTheActivity() {
        val pipe = FakePipe()
        var opened = 0
        val presence =
            DiscordPresence({ "123456" }) {
                opened++
                pipe
            }
        presence.update(
            Activity(
                "Solo Tower",
                "Chapter 12",
                1_700_000_000_000,
                "https://img.test/c.jpg",
                "Solo Tower",
            ),
        )
        presence.update(null)
        waitFor { pipe.sent.size == 3 }

        assertEquals(1, opened)
        assertEquals(DiscordPresence.OP_HANDSHAKE, pipe.sent[0].first)
        assertTrue(pipe.sent[0].second.contains(""""client_id":"123456""""))
        val set = pipe.sent[1].second
        assertTrue(
            set.contains(""""cmd":"SET_ACTIVITY"""") &&
                set.contains(""""details":"Solo Tower"""") &&
                set.contains(""""start":1700000000"""),
        )
        assertTrue(set.contains(""""large_image":"https://img.test/c.jpg""""))
        assertTrue(!pipe.sent[2].second.contains("activity"), "clearing sends no activity")
        presence.close()
    }

    @Test
    fun withoutAnAppIdNothingConnects() {
        var opened = 0
        val presence =
            DiscordPresence({ "" }) {
                opened++
                FakePipe()
            }
        presence.update(Activity("x", null, 0, null, null))
        Thread.sleep(100)
        assertEquals(0, opened)
        val frame = DiscordPresence.frame(1, "{}")
        assertEquals(listOf<Byte>(1, 0, 0, 0, 2, 0, 0, 0), frame.take(8))
        presence.close()
    }
}
