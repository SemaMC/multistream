package com.multistream.feature.streamingui

import android.content.Context
import android.media.projection.MediaProjection
import com.multistream.core.audio.GameAudioCapture
import com.multistream.core.capture.MediaProjectionConsentManager
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import com.multistream.core.encoding.AudioEncoder
import com.multistream.core.encoding.DualVideoEncoder
import com.multistream.core.encoding.MultiLayoutCompositor
import com.multistream.core.streaming.RTMPMultiStreamer
import com.multistream.core.widgets.WidgetLayoutManager
import com.multistream.data.platforms.SecureCredentialStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `starts the service before connecting and stops it when the session fails`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()

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

        val streamer = mockk<RTMPMultiStreamer>(relaxed = true)
        coEvery { streamer.connect(any()) } answers {
            calls += "connect"
            throw IllegalStateException("connect failed")
        }

        val widgetManager = mockk<WidgetLayoutManager>(relaxed = true)
        every { widgetManager.getWidgetsForLayout(any()) } returns MutableStateFlow(emptyList())

        val viewModel = StreamingViewModel(
            audioCapture = mockk<GameAudioCapture>(relaxed = true),
            audioEncoder = mockk<AudioEncoder>(relaxed = true),
            displayCapture = mockk<MediaProjectionDisplayCapture>(relaxed = true),
            compositor = mockk<MultiLayoutCompositor>(relaxed = true),
            encoder = mockk<DualVideoEncoder>(relaxed = true),
            streamer = streamer,
            widgetManager = widgetManager,
            consentManager = mockk<MediaProjectionConsentManager>(relaxed = true),
            credentialStore = mockk<SecureCredentialStore>(relaxed = true),
            context = context
        )

        viewModel.startStreaming(
            mediaProjection = mockk<MediaProjection>(relaxed = true),
            configs = listOf(PlatformConfig(Platform.TWITCH, "rtmp://example.test/app", "key", 6_000_000))
        )
        advanceUntilIdle()

        val serviceStart = calls.firstOrNull { it == "startService" || it == "startForegroundService" }
        assertEquals(
            listOf(serviceStart, "connect", "stopService"),
            calls
        )
        assertEquals("connect failed", viewModel.errorMessage.value)
    }
}
