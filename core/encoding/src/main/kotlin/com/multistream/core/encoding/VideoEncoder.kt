package com.multistream.core.encoding

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single encoded access unit produced by [VideoEncoder], before it is tagged with a layout.
 *
 * Kept layout agnostic on purpose: the same primitive feeds both independent encoders and the
 * layout is attached by [DualVideoEncoder], which is the only component that knows which pipeline
 * produced the chunk.
 */
data class EncodedChunkBase(
    val data: ByteArray,
    val timestamp: Long,
    val isKeyFrame: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedChunkBase) return false
        return data.contentEquals(other.data) &&
            timestamp == other.timestamp &&
            isKeyFrame == other.isKeyFrame
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        return result
    }
}

/**
 * Surface-input H.264 encoder used by both layouts.
 *
 * The encoder never fabricates frames: it reads whatever is rendered into its input surface. The
 * input surface is either supplied by the caller (the composition target produced by
 * [MultiLayoutCompositor]) or created by the codec through [MediaCodec.createInputSurface] when no
 * surface is passed. Codec deadlines are handled inside [encode]; callers are expected to keep
 * rendering frames into [inputSurface] while the flow is collected.
 *
 * Instances are stateful and single use per configuration; call [release] when the stream stops.
 */
open class VideoEncoder(
    private val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
) {
    @Volatile
    private var mediaCodec: MediaCodec? = null

    @Volatile
    private var inputSurface: Surface? = null

    @Volatile
    private var ownsInputSurface: Boolean = false

    private val released = AtomicBoolean(true)

    /** True while the underlying [MediaCodec] is started and has not been released. */
    val isConfigured: Boolean
        get() = !released.get() && mediaCodec != null

    /**
     * Configures the codec and starts it.
     *
     * @param inputSurface surface the compositor renders into. When `null` the codec creates and
     *   owns its own input surface and the created surface is returned.
     * @return the surface that must be used as the rendering target for this encoder.
     */
    open fun configure(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        inputSurface: Surface? = null
    ): Surface {
        require(width > 0 && height > 0) { "Invalid video size: ${width}x$height" }
        require(fps > 0) { "Invalid frame rate: $fps" }
        require(bitrate > 0) { "Invalid bitrate: $bitrate" }

        release()

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
            if (mimeType == MediaFormat.MIMETYPE_VIDEO_AVC) {
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
            }
        }

        val codec = MediaCodec.createEncoderByType(mimeType)
        var codecOwnsSurface = false
        val targetSurface = inputSurface ?: codec.createInputSurface().also {
            codecOwnsSurface = true
        }

        try {
            codec.configure(format, targetSurface, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (throwable: Throwable) {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (codecOwnsSurface) {
                runCatching { targetSurface.release() }
            }
            throw throwable
        }

        mediaCodec = codec
        this.inputSurface = targetSurface
        ownsInputSurface = codecOwnsSurface
        released.set(false)
        return targetSurface
    }

    /**
     * Cold flow of encoded frames. It drains the codec while the collector is active and completes
     * when the codec is released, reaches end of stream or the coroutine is cancelled.
     *
     * If the encoder was never configured the flow completes immediately.
     */
    open fun encode(): Flow<EncodedChunkBase> = flow {
        val codec = mediaCodec ?: return@flow
        val bufferInfo = MediaCodec.BufferInfo()

        while (currentCoroutineContext().isActive) {
            val outputIndex = try {
                codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            } catch (_: IllegalStateException) {
                // Codec stopped or released while the flow was still collecting.
                break
            }

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputIndex >= 0 -> {
                    val isCodecConfig =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val isEndOfStream =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val isKeyFrame =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                    val chunk = try {
                        if (bufferInfo.size > 0 && !isCodecConfig) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            val data = ByteArray(bufferInfo.size)
                            if (outputBuffer != null) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.get(data)
                            }
                            EncodedChunkBase(
                                data = data,
                                timestamp = bufferInfo.presentationTimeUs,
                                isKeyFrame = isKeyFrame
                            )
                        } else {
                            null
                        }
                    } catch (_: IllegalStateException) {
                        // The codec was released between dequeue and buffer access.
                        break
                    } finally {
                        runCatching { codec.releaseOutputBuffer(outputIndex, false) }
                    }

                    if (chunk != null) {
                        emit(chunk)
                    }
                    if (isEndOfStream) break
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    /** Asks the codec for an immediate IDR frame, used when a platform (re)connects. */
    open fun requestKeyFrame() {
        val codec = mediaCodec ?: return
        runCatching {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            codec.setParameters(params)
        }
    }

    /**
     * Stops and releases the codec. Safe to call multiple times and from any thread; a running
     * [encode] flow finishes as soon as the codec is gone.
     */
    open fun release() {
        released.set(true)
        val codec = mediaCodec ?: return
        mediaCodec = null
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (ownsInputSurface) {
            runCatching { inputSurface?.release() }
        }
        inputSurface = null
        ownsInputSurface = false
    }

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val I_FRAME_INTERVAL_SECONDS = 1
    }
}

/**
 * Creates [VideoEncoder] instances. Exists so [DualVideoEncoder] can be exercised with fake
 * encoders in unit tests without touching MediaCodec, and so a hardware/software encoder policy
 * can be introduced later without changing the public API.
 */
fun interface VideoEncoderFactory {
    fun create(): VideoEncoder
}

/** Production factory: one MediaCodec-backed encoder per call. */
class DefaultVideoEncoderFactory : VideoEncoderFactory {
    override fun create(): VideoEncoder = VideoEncoder()
}
