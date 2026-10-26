package com.multistream.feature.streamingui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multistream.core.audio.GameAudioCapture
import com.multistream.core.capture.MediaProjectionConsentManager
import com.multistream.core.common.HORIZONTAL_CONFIG
import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import com.multistream.core.common.Widget
import com.multistream.core.common.toConfig
import com.multistream.core.encoding.AudioEncoder
import com.multistream.core.encoding.DualVideoEncoder
import com.multistream.core.encoding.MultiLayoutCompositor
import com.multistream.core.streaming.RTMPMultiStreamer
import com.multistream.core.widgets.WidgetLayoutManager
import com.multistream.data.platforms.SecureCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives one dual-layout streaming session.
 *
 * The screen capture and the system audio capture share the same [MediaProjection]: the display
 * feeds [MultiLayoutCompositor] through a source surface it owns, and both layout encoders run
 * from the composition surfaces returned by [DualVideoEncoder.configure].
 *
 * Besides the dependencies named in the brief, the ViewModel also owns the consent flow
 * ([MediaProjectionConsentManager]) and loads the configured platforms from
 * [SecureCredentialStore], because the streaming screen is the only place that can request the
 * screen capture token and it needs at least one destination to start.
 */
@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val audioCapture: GameAudioCapture,
    private val audioEncoder: AudioEncoder,
    private val displayCapture: MediaProjectionDisplayCapture,
    private val compositor: MultiLayoutCompositor,
    private val encoder: DualVideoEncoder,
    private val streamer: RTMPMultiStreamer,
    private val widgetManager: WidgetLayoutManager,
    private val consentManager: MediaProjectionConsentManager,
    private val credentialStore: SecureCredentialStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _platforms = MutableStateFlow<List<PlatformConfig>>(emptyList())
    val platforms: StateFlow<List<PlatformConfig>> = _platforms.asStateFlow()

    val horizontalWidgets: StateFlow<List<Widget>> =
        widgetManager.getWidgetsForLayout(Layout.HORIZONTAL)
    val verticalWidgets: StateFlow<List<Widget>> =
        widgetManager.getWidgetsForLayout(Layout.VERTICAL)

    /** The whole session (setup + collectors + render loop). Cancelling it tears everything down. */
    private var streamingJob: Job? = null

    init {
        loadPlatforms()
    }

    /** Intent the screen must launch to obtain the MediaProjection consent token. */
    fun screenCaptureIntent(): Intent = consentManager.createConsentIntent()

    /**
     * Handles the result of [screenCaptureIntent] and starts the session with the platforms
     * already stored in [SecureCredentialStore].
     */
    fun onScreenCaptureResult(resultCode: Int, data: Intent?) {
        // A mediaProjection foreground service cannot start before the user grants consent:
        // startForeground() throws SecurityException without the OP_PROJECT_MEDIA grant. Check the
        // result first; getMediaProjection() still happens only after the service is up.
        if (resultCode != Activity.RESULT_OK || data == null) {
            _errorMessage.value = "Screen capture permission denied"
            return
        }

        viewModelScope.launch {
            try {
                // Android 14+ rejects getMediaProjection() unless a foreground service of type
                // mediaProjection is already running, so it has to start before the consent
                // result is consumed.
                startStreamingService()

                // startForeground() runs on the service's main-looper turn. A short wait is enough
                // for the MVP; a binder/StateFlow readiness signal is the robust alternative.
                delay(SERVICE_READINESS_DELAY_MS)

                val granted = consentManager.handleConsentResult(resultCode, data)
                if (!granted) {
                    _errorMessage.value = "Screen capture permission denied"
                    stopStreamingService()
                    return@launch
                }

                val projection = consentManager.getMediaProjection()
                if (projection == null) {
                    _errorMessage.value = "Screen capture permission denied"
                    stopStreamingService()
                    return@launch
                }

                startStreaming(projection, _platforms.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errorMessage.value = error.message ?: "Could not start screen capture"
                stopStreamingService()
            }
        }
    }

    /**
     * Starts capture, composition, both encoders and the RTMP fan-out.
     *
     * The call is idempotent while a session is starting or running. Setup errors are surfaced
     * through [errorMessage] and always release whatever was already allocated.
     */
    fun startStreaming(mediaProjection: MediaProjection, configs: List<PlatformConfig>) {
        if (_isStreaming.value || _isStarting.value) return

        if (configs.isEmpty()) {
            _errorMessage.value = "No platforms configured. Add one in Settings first."
            // The caller may have already started the mediaProjection foreground service: without
            // this the FGS would outlive the session, since the job (and its finally) never runs.
            stopStreamingService()
            return
        }

        _errorMessage.value = null
        _isStarting.value = true

        streamingJob = viewModelScope.launch {
            try {
                // Connect first: with no destination there is no point in allocating codecs.
                streamer.connect(configs)

                audioEncoder.configure(
                    sampleRate = SAMPLE_RATE,
                    channelCount = CHANNEL_COUNT,
                    bitRate = AUDIO_BITRATE
                )

                // The GL backend can only sample surfaces it created itself.
                val sourceSurface = compositor.createSourceSurface(
                    HORIZONTAL_CONFIG.width,
                    HORIZONTAL_CONFIG.height
                )
                displayCapture.start(
                    mediaProjection = mediaProjection,
                    width = HORIZONTAL_CONFIG.width,
                    height = HORIZONTAL_CONFIG.height,
                    dpi = CAPTURE_DPI,
                    target = sourceSurface
                )

                encoder.configure(
                    compositor = compositor,
                    sources = listOf(sourceSurface),
                    horizontalWidgets = horizontalWidgets.value,
                    verticalWidgets = verticalWidgets.value
                )

                _isStarting.value = false
                _isStreaming.value = true

                coroutineScope {
                    launch {
                        encoder.encode(Layout.HORIZONTAL).collect { chunk ->
                            streamer.send(chunk)
                        }
                    }

                    launch {
                        encoder.encode(Layout.VERTICAL).collect { chunk ->
                            streamer.send(chunk)
                        }
                    }

                    // MediaCodec calls inside encode() block, so keep audio off the main thread.
                    launch(Dispatchers.Default) {
                        audioCapture.startCapture(mediaProjection).collect { pcmData ->
                            audioEncoder.encode(pcmData, Layout.HORIZONTAL).collect { audioChunk ->
                                streamer.send(audioChunk)
                            }
                        }
                    }

                    launch {
                        val frameDelayMs = 1_000L / HORIZONTAL_CONFIG.fps
                        while (isActive) {
                            encoder.renderFrame()
                            delay(frameDelayMs)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _errorMessage.value = error.message ?: "Could not start streaming"
            } finally {
                _isStarting.value = false
                _isStreaming.value = false
                releaseResources()
                withContext(NonCancellable) {
                    runCatching { streamer.disconnectAll() }
                }
                stopStreamingService()
            }
        }
    }

    /** Stops the session immediately; the launched job releases every resource in its finally block. */
    fun stopStreaming() {
        if (!_isStreaming.value && !_isStarting.value) return

        _isStreaming.value = false
        _isStarting.value = false
        streamingJob?.cancel()
        streamingJob = null
    }

    fun addWidget(widget: Widget) {
        widgetManager.addWidget(widget)
    }

    fun removeWidget(widgetId: String) {
        widgetManager.removeWidget(widgetId)
    }

    override fun onCleared() {
        streamingJob?.cancel()
        streamingJob = null
        super.onCleared()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            _platforms.value = listOf(
                Platform.TWITCH,
                Platform.YOUTUBE,
                Platform.KICK,
                Platform.FACEBOOK
            )
                .mapNotNull { platform -> credentialStore.load(platform) }
                .map { credentials -> credentials.toConfig() }
        }
    }

    private fun releaseResources() {
        runCatching { audioCapture.stopCapture() }
        runCatching { audioEncoder.stop() }
        runCatching { displayCapture.release() }
        runCatching { encoder.release() }
        // Stops the projection itself, so the next session needs a fresh consent token.
        runCatching { consentManager.revokeConsent() }
    }

    private fun startStreamingService() {
        val serviceIntent = Intent(context, StreamingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopStreamingService() {
        context.stopService(Intent(context, StreamingService::class.java))
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 2
        const val AUDIO_BITRATE = 128_000
        const val CAPTURE_DPI = 320
        const val SERVICE_READINESS_DELAY_MS = 100L
    }
}

/**
 * MediaProjection-backed display capture used by [StreamingViewModel].
 *
 * `:core:capture` ships [MediaProjectionConsentManager] but not the
 * `MediaProjectionDisplayCapture` described in the design and Task 6. Until that class lands in
 * the core module, this feature-local implementation fills the gap.
 *
 * It renders into a surface supplied by the caller (in the streaming pipeline,
 * [MultiLayoutCompositor.createSourceSurface]) because the OpenGL backend can only sample source
 * surfaces it created itself.
 */
@Singleton
class MediaProjectionDisplayCapture @Inject constructor() {

    private var virtualDisplay: VirtualDisplay? = null

    fun start(
        mediaProjection: MediaProjection,
        width: Int,
        height: Int,
        dpi: Int,
        target: Surface
    ) {
        release()

        // Android 14+ requires a callback registered on the projection before the virtual display
        // is created, or createVirtualDisplay() fails.
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // The system stopped the projection (for example, the user revoked it).
                }
            },
            null
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            target,
            null,
            null
        )
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private companion object {
        const val VIRTUAL_DISPLAY_NAME = "MultistreamDisplay"
    }
}
