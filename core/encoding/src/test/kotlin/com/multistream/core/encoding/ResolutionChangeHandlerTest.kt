package com.multistream.core.encoding

import android.view.Surface
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Behaviour of [ResolutionChangeHandler]: restart the encoder at the new resolution and hand the
 * resulting sequence header to every streaming connection through [SequenceHeaderPropagator].
 *
 * The two extension functions under test live in `ResolutionChangeHandler.kt`, so they are mocked
 * at the file-facade level to keep the handler test focused on orchestration.
 */
class ResolutionChangeHandlerTest {

    private val handler = ResolutionChangeHandler()

    @Before
    fun setUp() {
        mockkStatic(RESOLUTION_CHANGE_HANDLER_FILE)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `reconfigures the encoder and propagates the new sequence header`() {
        val encoder = mockk<VideoEncoder>(relaxed = true)
        val propagator = mockk<SequenceHeaderPropagator>(relaxed = true)
        val sequenceHeader = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42)

        every { encoder.reconfigure(1280, 720) } just Runs
        every { encoder.waitForNextSequenceHeader() } returns sequenceHeader

        handler.onResolutionChange(1280, 720, encoder, propagator)

        verify(exactly = 1) { encoder.reconfigure(1280, 720) }
        verify(exactly = 1) { encoder.waitForNextSequenceHeader() }
        verify(exactly = 1) { propagator.propagateSequenceHeaderChange(sequenceHeader) }
    }

    @Test
    fun `propagates the exact header returned by the encoder`() {
        val encoder = mockk<VideoEncoder>(relaxed = true)
        val propagator = mockk<SequenceHeaderPropagator>(relaxed = true)
        val sequenceHeader = ByteArray(64) { it.toByte() }
        val propagated = mutableListOf<ByteArray>()

        every { encoder.reconfigure(any(), any()) } just Runs
        every { encoder.waitForNextSequenceHeader() } returns sequenceHeader
        every { propagator.propagateSequenceHeaderChange(capture(propagated)) } just Runs

        handler.onResolutionChange(1920, 1080, encoder, propagator)

        assertSame(sequenceHeader, propagated.single())
    }

    @Test
    fun `does not propagate when the encoder cannot be reconfigured`() {
        val encoder = mockk<VideoEncoder>(relaxed = true)
        val propagator = mockk<SequenceHeaderPropagator>(relaxed = true)

        every { encoder.reconfigure(any(), any()) } throws IllegalStateException("codec busy")

        try {
            handler.onResolutionChange(1280, 720, encoder, propagator)
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertEquals("codec busy", expected.message)
        }

        verify(exactly = 0) { encoder.waitForNextSequenceHeader() }
        verify(exactly = 0) { propagator.propagateSequenceHeaderChange(any()) }
    }

    private companion object {
        const val RESOLUTION_CHANGE_HANDLER_FILE =
            "com.multistream.core.encoding.ResolutionChangeHandlerKt"
    }
}

/**
 * Behaviour of the [VideoEncoder] extension functions themselves, exercised through a recording
 * fake so no MediaCodec is needed.
 */
class VideoEncoderReconfigureTest {

    @Test
    fun `reconfigure releases the current codec before configuring the new resolution`() {
        val encoder = RecordingVideoEncoder()

        encoder.reconfigure(1280, 720)

        assertEquals(listOf("release", "configure"), encoder.calls)
        assertEquals(1280, encoder.configuredWidth)
        assertEquals(720, encoder.configuredHeight)
        assertEquals(60, encoder.configuredFps)
        assertEquals(6_000_000, encoder.configuredBitrate)
    }

    /**
     * The brief defines the header as a placeholder until device-backed CSD capture is wired, so
     * the contract verified here is that a header buffer is always returned without throwing.
     */
    @Test
    fun `waitForNextSequenceHeader returns a header buffer`() {
        val header = RecordingVideoEncoder().waitForNextSequenceHeader()

        assertNotNull(header)
        assertTrue(header.isEmpty())
    }

    private class RecordingVideoEncoder : VideoEncoder() {
        val calls = mutableListOf<String>()
        var configuredWidth = 0
            private set
        var configuredHeight = 0
            private set
        var configuredFps = 0
            private set
        var configuredBitrate = 0
            private set

        private val ownSurface = mockk<Surface>(relaxed = true)

        override fun release() {
            calls += "release"
        }

        override fun configure(
            width: Int,
            height: Int,
            fps: Int,
            bitrate: Int,
            inputSurface: Surface?
        ): Surface {
            calls += "configure"
            configuredWidth = width
            configuredHeight = height
            configuredFps = fps
            configuredBitrate = bitrate
            return inputSurface ?: ownSurface
        }
    }
}
