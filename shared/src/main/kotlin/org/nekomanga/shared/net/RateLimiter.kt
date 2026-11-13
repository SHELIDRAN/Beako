package org.nekomanga.shared.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Allows at most [permits] calls in any window of [periodMillis], making later callers wait. Used
 * to stay inside each API's published rate limit.
 */
class RateLimiter(
    private val permits: Int,
    private val periodMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    init {
        require(permits > 0 && periodMillis > 0)
    }

    private val mutex = Mutex()
    private val recent = ArrayDeque<Long>()

    suspend fun acquire() {
        mutex.withLock {
            while (true) {
                val time = now()
                while (recent.isNotEmpty() && time - recent.first() >= periodMillis) {
                    recent.removeFirst()
                }
                if (recent.size < permits) {
                    recent.addLast(time)
                    return
                }
                sleep(periodMillis - (time - recent.first()))
            }
        }
    }
}
