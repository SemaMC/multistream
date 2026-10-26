package com.multistream.core.streaming

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Decides what to do for each [RTMPError] and executes the recovery.
 *
 * Transient transport failures are retried with exponential backoff (1s, 2s, 4s,
 * 8s; five attempts in total). Recoverable protocol failures get one specific
 * recovery action and a single retry. Permanent failures are rethrown untouched so
 * the caller can stop the platform and notify the user.
 *
 * [RetryPolicy] is created per retry instead of shared: every error starts again at
 * the 1s delay, and two platforms failing at the same time cannot consume each
 * other's backoff state.
 */
@Singleton
class ErrorHandler @Inject constructor() {

    suspend fun <T> handleError(error: RTMPError, block: suspend () -> T): T {
        return when (error) {
            RTMPError.CONNECTION_CLOSED,
            RTMPError.INGEST_CONGESTION,
            RTMPError.NETWORK_UNAVAILABLE -> retryWithBackoff(block)
            RTMPError.AUTH_EXPIRED -> {
                refreshToken()
                block()
            }
            RTMPError.TOO_FREQUENT -> {
                delay(TOO_FREQUENT_DELAY_MS)
                block()
            }
            RTMPError.FLV_MUX_ERROR -> {
                resetConnection()
                block()
            }
            RTMPError.INVALID_STREAM_KEY,
            RTMPError.RTMP_HANDSHAKE_FAILED,
            RTMPError.PROJECTION_REVOKED -> throw error
        }
    }

    private suspend fun <T> retryWithBackoff(block: suspend () -> T): T =
        RetryPolicy(maxAttempts = MAX_ATTEMPTS).retry(block)

    /** Placeholder until the auth layer exposes a token refresh entry point. */
    private suspend fun refreshToken() {
        // No-op: wired in the auth integration task.
    }

    /** Placeholder until the RTMP client exposes a connection reset entry point. */
    private suspend fun resetConnection() {
        // No-op: wired when the real RTMP client is bound.
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val TOO_FREQUENT_DELAY_MS = 60_000L
    }
}
