package com.multistream.core.streaming

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Retries a suspending block with exponential backoff.
 *
 * The block is attempted at most [maxAttempts] times (1 means "no retry"). After a
 * successful attempt the backoff is reset, so a later failure starts again from the
 * initial delay instead of the last (maximum) one.
 *
 * [CancellationException] is never retried; it is rethrown so coroutine cancellation
 * keeps working.
 */
class RetryPolicy(
    val maxAttempts: Int = 5,
    val backoff: ExponentialBackoff = ExponentialBackoff()
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1 (was $maxAttempts)" }
    }

    suspend fun <T> retry(block: suspend () -> T): T {
        var attempts = 0
        while (true) {
            try {
                val result = block()
                backoff.reset()
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempts++
                if (attempts >= maxAttempts) throw e
                delay(backoff.nextDelay())
            }
        }
    }
}

/**
 * Exponential backoff: 1s, 2s, 4s, ... capped at 30s by default.
 *
 * [nextDelay] is synchronized because a single policy may be used by several
 * concurrently connecting platforms.
 */
class ExponentialBackoff(
    private val initial: Long = 1_000L,
    private val max: Long = 30_000L
) {
    init {
        require(initial > 0L) { "initial must be positive (was $initial)" }
        require(max >= initial) { "max ($max) must be >= initial ($initial)" }
    }

    private var current = initial

    @Synchronized
    fun nextDelay(): Long {
        val delay = current
        current = (current * 2).coerceAtMost(max)
        return delay
    }

    @Synchronized
    fun reset() {
        current = initial
    }
}
