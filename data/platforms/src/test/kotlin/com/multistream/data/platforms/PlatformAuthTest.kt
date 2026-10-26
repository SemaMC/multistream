package com.multistream.data.platforms

import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Local unit tests for the platform authenticators.
 *
 * [SecureCredentialStore] is mocked: these tests cover the authentication contract (check stored
 * credentials first, fall back to the MVP flow, persist only on manual entry) and its encryption
 * is already covered by [SecureCredentialStoreTest].
 */
class PlatformAuthTest {

    private lateinit var credentialStore: SecureCredentialStore

    @Before
    fun setUp() {
        credentialStore = mockk()
    }

    @Test
    fun `TwitchAuth performs OAuth flow`() = runTest {
        coEvery { credentialStore.load(Platform.TWITCH) } returns null

        val credentials = TwitchAuth(credentialStore).login()

        assertEquals(Platform.TWITCH, credentials.platform)
        assertEquals("rtmp://live.twitch.tv/app/", credentials.rtmpUrl)
        assertEquals("", credentials.streamKey)
        coVerify(exactly = 1) { credentialStore.load(Platform.TWITCH) }
        // The fallback has no stream key yet, so nothing usable is persisted.
        coVerify(exactly = 0) { credentialStore.save(any()) }
    }

    @Test
    fun `YouTubeAuth performs OAuth flow`() = runTest {
        coEvery { credentialStore.load(Platform.YOUTUBE) } returns null

        val credentials = YouTubeAuth(credentialStore).login()

        assertEquals(Platform.YOUTUBE, credentials.platform)
        assertEquals("rtmp://a.rtmp.youtube.com/live2/", credentials.rtmpUrl)
        assertEquals("", credentials.streamKey)
        coVerify(exactly = 1) { credentialStore.load(Platform.YOUTUBE) }
        coVerify(exactly = 0) { credentialStore.save(any()) }
    }

    @Test
    fun `FacebookAuth performs OAuth flow`() = runTest {
        coEvery { credentialStore.load(Platform.FACEBOOK) } returns null

        val credentials = FacebookAuth(credentialStore).login()

        assertEquals(Platform.FACEBOOK, credentials.platform)
        assertEquals("rtmps://live-api-s.facebook.com:443/rtmp/", credentials.rtmpUrl)
        assertEquals("", credentials.streamKey)
        // Facebook only accepts RTMPS over port 443.
        assertTrue(credentials.rtmpUrl.startsWith("rtmps://"))
        assertTrue(credentials.rtmpUrl.contains(":443/"))
        coVerify(exactly = 1) { credentialStore.load(Platform.FACEBOOK) }
        coVerify(exactly = 0) { credentialStore.save(any()) }
    }

    @Test
    fun `TwitchAuth returns stored credentials without starting the OAuth flow`() = runTest {
        val stored = PlatformCredentials(
            Platform.TWITCH,
            "rtmp://live.twitch.tv/app/",
            "live_123456_abcdef"
        )
        coEvery { credentialStore.load(Platform.TWITCH) } returns stored

        val credentials = TwitchAuth(credentialStore).login()

        assertEquals(stored, credentials)
        coVerify(exactly = 1) { credentialStore.load(Platform.TWITCH) }
        coVerify(exactly = 0) { credentialStore.save(any()) }
    }

    @Test
    fun `YouTubeAuth returns stored credentials without starting the OAuth flow`() = runTest {
        val stored = PlatformCredentials(
            Platform.YOUTUBE,
            "rtmp://a.rtmp.youtube.com/live2/",
            "youtube-stream-key"
        )
        coEvery { credentialStore.load(Platform.YOUTUBE) } returns stored

        assertEquals(stored, YouTubeAuth(credentialStore).login())
        coVerify(exactly = 1) { credentialStore.load(Platform.YOUTUBE) }
        coVerify(exactly = 0) { credentialStore.save(any()) }
    }

    @Test
    fun `FacebookAuth returns stored credentials without starting the OAuth flow`() = runTest {
        val stored = PlatformCredentials(
            Platform.FACEBOOK,
            "rtmps://live-api-s.facebook.com:443/rtmp/",
            "facebook-stream-key"
        )
        coEvery { credentialStore.load(Platform.FACEBOOK) } returns stored

        assertEquals(stored, FacebookAuth(credentialStore).login())
        coVerify(exactly = 1) { credentialStore.load(Platform.FACEBOOK) }
        coVerify(exactly = 0) { credentialStore.save(any()) }
    }

    @Test
    fun `ManualAuth stores credentials securely`() = runTest {
        coEvery { credentialStore.save(any()) } returns Unit
        val expected = PlatformCredentials(
            Platform.KICK,
            "rtmps://fa723fc1.kick.com/app/",
            "kick-stream-key"
        )

        val credentials = ManualAuth(credentialStore).login(
            platform = Platform.KICK,
            rtmpUrl = "rtmps://fa723fc1.kick.com/app/",
            streamKey = "kick-stream-key"
        )

        assertEquals(expected, credentials)
        coVerify(exactly = 1) { credentialStore.save(expected) }
        // Manual entry never reads the store: the user supplies every value.
        coVerify(exactly = 0) { credentialStore.load(any()) }
    }

    @Test
    fun `ManualAuth accepts custom RTMP endpoints`() = runTest {
        coEvery { credentialStore.save(any()) } returns Unit
        val expected = PlatformCredentials(
            Platform.CUSTOM,
            "rtmp://127.0.0.1:1935/live",
            "custom-key"
        )

        val credentials = ManualAuth(credentialStore).login(
            platform = Platform.CUSTOM,
            rtmpUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "custom-key"
        )

        assertEquals(expected, credentials)
        coVerify(exactly = 1) { credentialStore.save(expected) }
    }
}
