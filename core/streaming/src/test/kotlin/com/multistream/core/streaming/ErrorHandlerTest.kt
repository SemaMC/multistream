package com.multistream.core.streaming

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorHandlerTest {

    private val handler = ErrorHandler()

    @Test
    fun `retries CONNECTION_CLOSED with exponential backoff until success`() = runTest {
        var attempts = 0

        val result = handler.handleError(RTMPError.CONNECTION_CLOSED) {
            attempts++
            if (attempts < 3) throw RTMPError.CONNECTION_CLOSED
            "connected"
        }

        assertEquals("connected", result)
        assertEquals(3, attempts)
        // 1s + 2s of virtual backoff before the third attempt.
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test
    fun `retries INGEST_CONGESTION five times and rethrows the last error`() = runTest {
        var attempts = 0

        val thrown = runCatching {
            handler.handleError(RTMPError.INGEST_CONGESTION) {
                attempts++
                throw RTMPError.INGEST_CONGESTION
            }
        }.exceptionOrNull()

        assertEquals(5, attempts)
        assertSame(RTMPError.INGEST_CONGESTION, thrown)
        // 1s + 2s + 4s + 8s; the 16s delay would only follow a sixth attempt.
        assertEquals(15_000L, testScheduler.currentTime)
    }

    @Test
    fun `retries NETWORK_UNAVAILABLE with one second before the second attempt`() = runTest {
        var attempts = 0

        handler.handleError(RTMPError.NETWORK_UNAVAILABLE) {
            attempts++
            if (attempts < 2) throw RTMPError.NETWORK_UNAVAILABLE
        }

        assertEquals(2, attempts)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun `does not run the recovery block on INVALID_STREAM_KEY`() = runTest {
        var attempts = 0
        var thrown: RTMPError? = null

        try {
            handler.handleError(RTMPError.INVALID_STREAM_KEY) {
                attempts++
            }
        } catch (e: RTMPError) {
            thrown = e
        }

        // Non-retryable: the error is rethrown immediately, no recovery is attempted.
        assertEquals(0, attempts)
        assertSame(RTMPError.INVALID_STREAM_KEY, thrown)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `does not run the recovery block on RTMP_HANDSHAKE_FAILED`() = runTest {
        var attempts = 0

        try {
            handler.handleError(RTMPError.RTMP_HANDSHAKE_FAILED) {
                attempts++
            }
        } catch (e: RTMPError) {
            assertSame(RTMPError.RTMP_HANDSHAKE_FAILED, e)
        }

        assertEquals(0, attempts)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `does not run the recovery block on PROJECTION_REVOKED`() = runTest {
        var attempts = 0

        try {
            handler.handleError(RTMPError.PROJECTION_REVOKED) {
                attempts++
            }
        } catch (e: RTMPError) {
            assertSame(RTMPError.PROJECTION_REVOKED, e)
        }

        assertEquals(0, attempts)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `AUTH_EXPIRED runs the recovery block once without backoff`() = runTest {
        var attempts = 0

        val result = handler.handleError(RTMPError.AUTH_EXPIRED) {
            attempts++
            "reauthenticated"
        }

        assertEquals("reauthenticated", result)
        assertEquals(1, attempts)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `AUTH_EXPIRED propagates the failure instead of retrying with backoff`() = runTest {
        var attempts = 0

        try {
            handler.handleError(RTMPError.AUTH_EXPIRED) {
                attempts++
                throw RTMPError.AUTH_EXPIRED
            }
        } catch (e: RTMPError) {
            assertSame(RTMPError.AUTH_EXPIRED, e)
        }

        assertEquals(1, attempts)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `TOO_FREQUENT waits sixty seconds before running the recovery block once`() = runTest {
        var attempts = 0

        val result = handler.handleError(RTMPError.TOO_FREQUENT) {
            attempts++
            "resumed"
        }

        assertEquals("resumed", result)
        assertEquals(1, attempts)
        assertEquals(60_000L, testScheduler.currentTime)
    }

    @Test
    fun `FLV_MUX_ERROR runs the recovery block once without backoff`() = runTest {
        var attempts = 0

        val result = handler.handleError(RTMPError.FLV_MUX_ERROR) {
            attempts++
            "remuxed"
        }

        assertEquals("remuxed", result)
        assertEquals(1, attempts)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `retries do not swallow coroutine cancellation`() = runTest {
        var attempts = 0

        try {
            handler.handleError(RTMPError.CONNECTION_CLOSED) {
                attempts++
                throw CancellationException("cancelled")
            }
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }

        assertEquals(1, attempts)
    }
}
