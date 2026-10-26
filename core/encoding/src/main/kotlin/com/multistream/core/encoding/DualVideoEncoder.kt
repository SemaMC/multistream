package com.multistream.core.encoding

import android.view.Surface
import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.HORIZONTAL_CONFIG
import com.multistream.core.common.Layout
import com.multistream.core.common.VERTICAL_CONFIG
import com.multistream.core.common.Widget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the two independent video pipelines: horizontal (1920x1080) and vertical (1080x1920).
 *
 * [configure] wires each encoder to its own composition surface, so both layouts are rendered and
 * encoded at the same time from the same sources and their own widget set. Every encoded access
 * unit is tagged with the layout that produced it through [encode], which is what the streaming
 * router needs to send each platform the right stream.
 *
 * The two flows returned by [encode] are independent: collecting one does not start the other,
 * and each encoder has its own MediaCodec instance.
 */
@Singleton
class DualVideoEncoder @Inject constructor(
    private val encoderFactory: VideoEncoderFactory
) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Volatile
    private var horizontalEncoder: VideoEncoder? = null

    @Volatile
    private var verticalEncoder: VideoEncoder? = null

    @Volatile
    private var activeCompositor: MultiLayoutCompositor? = null

    @Volatile
    private var activeComposition: CompositionResult? = null

    /**
     * Creates both encoders and starts composition.
     *
     * Each encoder gets its own codec-owned input surface; those surfaces are handed to the
     * compositor as render targets. Widgets are rendered during this call, so it suspends while the
     * WebViews load.
     *
     * @return the two composition surfaces now feeding the encoders.
     */
    suspend fun configure(
        compositor: MultiLayoutCompositor,
        sources: List<Surface>,
        horizontalWidgets: List<Widget>,
        verticalWidgets: List<Widget>
    ): CompositionResult = mutex.withLock {
        releaseEncoders()

        val horizontal = encoderFactory.create()
        val vertical = encoderFactory.create()

        try {
            val horizontalSurface = horizontal.configure(
                width = HORIZONTAL_CONFIG.width,
                height = HORIZONTAL_CONFIG.height,
                fps = HORIZONTAL_CONFIG.fps,
                bitrate = HORIZONTAL_CONFIG.bitrate
            )
            val verticalSurface = vertical.configure(
                width = VERTICAL_CONFIG.width,
                height = VERTICAL_CONFIG.height,
                fps = VERTICAL_CONFIG.fps,
                bitrate = VERTICAL_CONFIG.bitrate
            )

            val result = compositor.compose(
                sources = sources,
                horizontalWidgets = horizontalWidgets,
                verticalWidgets = verticalWidgets,
                horizontalTarget = horizontalSurface,
                verticalTarget = verticalSurface
            )

            horizontalEncoder = horizontal
            verticalEncoder = vertical
            activeCompositor = compositor
            activeComposition = result
            result
        } catch (throwable: Throwable) {
            horizontal.release()
            vertical.release()
            throw throwable
        }
    }

    /**
     * Encoded stream for [layout].
     *
     * The flow completes immediately when the encoder has not been configured or has been
     * released, so collectors can be started before [configure] without crashing.
     */
    fun encode(layout: Layout): Flow<EncodedChunk> = flow {
        val encoder = getEncoder(layout) ?: return@flow
        encoder.encode().collect { chunk ->
            emit(
                EncodedChunk(
                    data = chunk.data,
                    timestamp = chunk.timestamp,
                    isKeyFrame = chunk.isKeyFrame,
                    layout = layout,
                    isVideo = true
                )
            )
        }
    }

    /**
     * Renders one more frame into both composition surfaces. Call it at the capture frame rate to
     * keep the encoders fed after [configure] produced the first frame.
     */
    suspend fun renderFrame(): Boolean = activeCompositor?.renderFrame() ?: false

    /** Encoder for [layout], or `null` when not configured. */
    fun getEncoder(layout: Layout): VideoEncoder? = when (layout) {
        Layout.HORIZONTAL -> horizontalEncoder
        Layout.VERTICAL -> verticalEncoder
    }

    /** Surfaces currently feeding the encoders, or `null` before [configure]. */
    val composition: CompositionResult?
        get() = activeComposition

    /**
     * Releases both codecs and the composition backend.
     *
     * The compositor teardown is dispatched to a background coroutine because this method is
     * synchronous (it is called from `onStop`/`stopStreaming`), while releasing the GL context is
     * a suspending operation.
     */
    fun release() {
        releaseEncoders()
        val compositor = activeCompositor
        activeCompositor = null
        activeComposition = null
        if (compositor != null) {
            cleanupScope.launch { compositor.release() }
        }
    }

    private fun releaseEncoders() {
        horizontalEncoder?.release()
        verticalEncoder?.release()
        horizontalEncoder = null
        verticalEncoder = null
    }
}
