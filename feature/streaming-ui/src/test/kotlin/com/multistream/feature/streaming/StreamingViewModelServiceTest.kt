package com.multistream.feature.streamingui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import com.multistream.core.audio.GameAudioCapture
import com.multistream.core.capture.MediaProjectionConsentManager
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials
import com.multistream.core.encoding.AudioEncoder
import com.multistream.core.encoding.DualVideoEncoder
import com.multistream.core.encoding.MultiLayoutCompositor
import com.multistream.core.streaming.RTMPMultiStreamer
import com.multistream.core.widgets.WidgetLayoutManager
import com.multistream.data.platforms.SecureCredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingViewModelServiceTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onScreenCaptureResult starts the service before obtaining the projection and stops it when the session fails`() =
        runTest(dispatcher) {
            val calls = mutableListOf<String>()

            val context = recordingContext(calls)
            val consentManager = recordingConsentManager(calls)
            val streamer = mockk<RTMPMultiStreamer>(relaxed = true)
            coEvery { streamer.connect(any()) } answers {
                calls += "connect"
                throw IllegalStateException("connect failed")
            }

            val viewModel = createViewModel(
                context = context,
                streamer = streamer,
                consentManager = consentManager
            )

            viewModel.onScreenCaptureResult(Activity.RESULT_OK, mockk<Intent>(relaxed = true))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    firstServiceStart(calls),
                    "getMediaProjection",
                    "connect",
                    "stopService"
                ),
                calls
            )
            assertEquals("connect failed", viewModel.errorMessage.value)
        }

    @Test
    fun `onScreenCaptureResult stops the service when consent is denied`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()

        val context = recordingContext(calls)
        val consentManager = recordingConsentManager(calls)
        val streamer = mockk<RTMPMultiStreamer>(relaxed = true)

        val viewModel = createViewModel(
            context = context,
            streamer = streamer,
            consentManager = consentManager
        )

        viewModel.onScreenCaptureResult(Activity.RESULT_CANCELED, null)
        advanceUntilIdle()

        // The mediaProjection foreground service cannot start without the consent grant
        // (startForeground throws SecurityException), so a denial must not start it at all.
        assertEquals(emptyList<String>(), calls)
        assertEquals("Screen capture permission denied", viewModel.errorMessage.value)
        coVerify(exactly = 0) { streamer.connect(any()) }
    }

    @Test
    fun `onScreenCaptureResult streams and stops the service when the session is stopped`() =
        runTest(dispatcher) {
            val calls = mutableListOf<String>()

            val context = recordingContext(calls)
            val projection = mockk<MediaProjection>(relaxed = true)
            val consentManager = recordingConsentManager(
                calls = calls,
                projection = projection
            )

            val streamer = mockk<RTMPMultiStreamer>(relaxed = true)
            coEvery { streamer.connect(any()) } answers { calls += "connect" }

            val displayCapture = mockk<MediaProjectionDisplayCapture>(relaxed = true)
            every { displayCapture.start(any(), any(), any(), any(), any()) } answers {
                calls += "displayCapture.start"
            }

            val compositor = mockk<MultiLayoutCompositor>(relaxed = true)
            coEvery { compositor.createSourceSurface(any(), any()) } returns
                mockk<Surface>(relaxed = true)

            val encoder = mockk<DualVideoEncoder>(relaxed = true)
            every { encoder.encode(any()) } returns emptyFlow()

            val audioCapture = mockk<GameAudioCapture>(relaxed = true)
            every { audioCapture.startCapture(any()) } returns emptyFlow()

            val viewModel = createViewModel(
                context = context,
                streamer = streamer,
                consentManager = consentManager,
                audioCapture = audioCapture,
                displayCapture = displayCapture,
                compositor = compositor,
                encoder = encoder
            )

            viewModel.onScreenCaptureResult(Activity.RESULT_OK, mockk<Intent>(relaxed = true))
            advanceTimeBy(100)
            runCurrent()

            assertEquals(
                listOf(
                    firstServiceStart(calls),
                    "getMediaProjection",
                    "connect",
                    "displayCapture.start"
                ),
                calls
            )
            assertTrue(viewModel.isStreaming.value)

            viewModel.stopStreaming()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    firstServiceStart(calls),
                    "getMediaProjection",
                    "connect",
                    "displayCapture.start",
                    "stopService"
                ),
                calls
            )
        }

    @Test
    fun `display capture registers a projection callback before creating the virtual display`() {
        val calls = mutableListOf<String>()

        val projection = mockk<MediaProjection>(relaxed = true)
        every { projection.registerCallback(any(), isNull()) } answers {
            calls += "registerCallback"
        }
        every {
            projection.createVirtualDisplay(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull()
            )
        } answers {
            calls += "createVirtualDisplay"
            mockk<VirtualDisplay>(relaxed = true)
        }

        MediaProjectionDisplayCapture().start(
            mediaProjection = projection,
            width = 1280,
            height = 720,
            dpi = 320,
            target = mockk<Surface>(relaxed = true)
        )

        assertEquals(listOf("registerCallback", "createVirtualDisplay"), calls)
    }

    private fun recordingContext(calls: MutableList<String>): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.startService(any()) } answers {
            calls += "startService"
            null
        }
        every { context.startForegroundService(any()) } answers {
            calls += "startForegroundService"
            null
        }
        every { context.stopService(any()) } answers {
            calls += "stopService"
            true
        }
        return context
    }

    /**
     * Real [MediaProjectionConsentManager] backed by a mocked [MediaProjectionManager], so the
     * projection acquisition (`getMediaProjection`, called from `handleConsentResult`) is observed
     * at the platform boundary. Mocking the manager itself is not viable: Kotlin compiles the
     * `mediaProjection` property getter and the `getMediaProjection()` function to methods with the
     * same name, and MockK routes the call to the real implementation.
     */
    private fun recordingConsentManager(
        calls: MutableList<String>,
        projection: MediaProjection = mockk(relaxed = true)
    ): MediaProjectionConsentManager {
        val context = mockk<Context>(relaxed = true)
        val projectionManager = mockk<MediaProjectionManager>(relaxed = true)
        every { context.getSystemService(any()) } returns projectionManager
        every { projectionManager.getMediaProjection(any(), any()) } answers {
            calls += "getMediaProjection"
            projection
        }
        return MediaProjectionConsentManager(context)
    }

    private fun createViewModel(
        context: Context,
        streamer: RTMPMultiStreamer,
        consentManager: MediaProjectionConsentManager,
        audioCapture: GameAudioCapture = mockk(relaxed = true),
        displayCapture: MediaProjectionDisplayCapture = mockk(relaxed = true),
        compositor: MultiLayoutCompositor = mockk(relaxed = true),
        encoder: DualVideoEncoder = mockk(relaxed = true)
    ): StreamingViewModel {
        val widgetManager = mockk<WidgetLayoutManager>(relaxed = true)
        every { widgetManager.getWidgetsForLayout(any()) } returns MutableStateFlow(emptyList())

        val credentialStore = mockk<SecureCredentialStore>(relaxed = true)
        coEvery { credentialStore.load(any()) } returns PlatformCredentials(
            platform = Platform.TWITCH,
            rtmpUrl = "rtmp://example.test/app",
            streamKey = "key"
        )

        return StreamingViewModel(
            audioCapture = audioCapture,
            audioEncoder = mockk<AudioEncoder>(relaxed = true),
            displayCapture = displayCapture,
            compositor = compositor,
            encoder = encoder,
            streamer = streamer,
            widgetManager = widgetManager,
            consentManager = consentManager,
            credentialStore = credentialStore,
            context = context
        )
    }

    private fun firstServiceStart(calls: List<String>): String =
        calls.first { it == "startService" || it == "startForegroundService" }
}
