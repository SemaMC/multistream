package com.multistream.feature.settings

import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials
import com.multistream.data.platforms.ManualAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Local unit tests for [PlatformConfigViewModel].
 *
 * [ManualAuth] is mocked: the credential encryption and storage contract is covered by
 * `PlatformAuthTest` in `:data:platforms`. These tests cover the configuration state machine the
 * screen drives (which platforms are offered, the preloaded ingest URL and what saving does).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformConfigViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var manualAuth: ManualAuth
    private lateinit var viewModel: PlatformConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        manualAuth = mockk()
        viewModel = PlatformConfigViewModel(manualAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `platform list includes every configurable platform`() {
        assertEquals(
            listOf(Platform.TWITCH, Platform.YOUTUBE, Platform.KICK, Platform.FACEBOOK, Platform.CUSTOM),
            viewModel.platforms.value
        )
    }

    @Test
    fun `startConfiguration preloads the default ingest url of each platform`() {
        val expected = mapOf(
            Platform.TWITCH to "rtmp://live.twitch.tv/app/",
            Platform.YOUTUBE to "rtmp://a.rtmp.youtube.com/live2/",
            Platform.KICK to "rtmp://kick.livepush.com/live/",
            Platform.FACEBOOK to "rtmps://live-api-s.facebook.com:443/rtmp/",
            Platform.CUSTOM to ""
        )

        expected.forEach { (platform, url) ->
            viewModel.startConfiguration(platform)
            assertEquals(
                ConfigurationState.AwaitingStreamKey(platform, url),
                viewModel.state.value
            )
        }
    }

    @Test
    fun `saveCredentials persists the entered url and key`() {
        val credentials = PlatformCredentials(
            Platform.TWITCH,
            "rtmp://live.twitch.tv/app/",
            "live_123456_abcdef"
        )
        coEvery {
            manualAuth.login(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "live_123456_abcdef")
        } returns credentials

        viewModel.saveCredentials(
            Platform.TWITCH,
            "rtmp://live.twitch.tv/app/",
            "live_123456_abcdef"
        )

        coVerify(exactly = 1) {
            manualAuth.login(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "live_123456_abcdef")
        }
        assertEquals(ConfigurationState.Success, viewModel.state.value)
    }

    @Test
    fun `saveCredentials reports the failure message`() {
        coEvery { manualAuth.login(any(), any(), any()) } throws IllegalStateException("Keystore unavailable")

        viewModel.saveCredentials(Platform.CUSTOM, "rtmp://127.0.0.1:1935/live", "custom-key")

        assertEquals(
            ConfigurationState.Error("Keystore unavailable"),
            viewModel.state.value
        )
    }

    @Test
    fun `resetState returns to idle`() {
        viewModel.startConfiguration(Platform.KICK)

        viewModel.resetState()

        assertEquals(ConfigurationState.Idle, viewModel.state.value)
    }
}
