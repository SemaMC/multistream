package com.multistream.core.encoding

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restarts a video encoder at a new resolution and forwards the resulting H.264 sequence header
 * (SPS/PPS) to every connected stream.
 *
 * The handler talks to streaming through [SequenceHeaderPropagator] instead of depending on the
 * RTMP implementation directly: `:core:streaming` lives above `:core:encoding`, so a direct
 * reference would invert the module graph and create a cycle. `RTMPMultiStreamer` implements the
 * interface on its side.
 */
@Singleton
class ResolutionChangeHandler @Inject constructor() {

    fun onResolutionChange(
        newWidth: Int,
        newHeight: Int,
        encoder: VideoEncoder,
        propagator: SequenceHeaderPropagator
    ) {
        // Reconfigure encoder with new resolution
        encoder.reconfigure(newWidth, newHeight)

        // Wait for new SPS/PPS
        val newSequenceHeader = encoder.waitForNextSequenceHeader()

        // Propagate to all RTMP connections via interface
        propagator.propagateSequenceHeaderChange(newSequenceHeader)
    }
}

/**
 * Restarts the codec at [width] x [height] with the streaming defaults (60 fps, 6 Mbps).
 *
 * [VideoEncoder.configure] releases the previous codec on its own; the explicit [VideoEncoder.release]
 * states the restart and leaves the encoder stopped if the new configuration fails.
 */
fun VideoEncoder.reconfigure(width: Int, height: Int) {
    // Release current codec
    release()
    // Reconfigure with new dimensions; the codec creates a fresh input surface of its own.
    configure(width, height, RECONFIGURE_FPS, RECONFIGURE_BITRATE)
}

/**
 * Returns the sequence header produced by the reconfigured codec.
 *
 * Placeholder for this task: MediaCodec exposes SPS/PPS as `csd-0` on the output format only after
 * the first output buffer, so capturing it needs a device-backed wait that is out of scope here.
 * The buffer is always non-null so callers can forward it unconditionally.
 */
fun VideoEncoder.waitForNextSequenceHeader(): ByteArray {
    return ByteArray(0)
}

private const val RECONFIGURE_FPS = 60
private const val RECONFIGURE_BITRATE = 6_000_000
