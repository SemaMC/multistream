package com.multistream.core.streaming

import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RTMPConnectionTest {
    private lateinit var mockClient: RtmpClient
    private lateinit var connection: RTMPConnection
    private val config = PlatformConfig(
        platform = Platform.TWITCH,
        rtmpUrl = "rtmp://live.twitch.tv/app",
        streamKey = "test_key",
        bitrate = 6_000_000
    )

    @Before
    fun setup() {
        mockClient = mockk(relaxed = true)
        connection = RTMPConnection(config, Layout.HORIZONTAL) { mockClient }
    }

    @Test
    fun `connect creates client and connects`() = runTest {
        assertFalse(connection.isConnected)
        
        connection.connect()
        
        verify { mockClient.connect("rtmp://live.twitch.tv/app", "test_key") }
        assertTrue(connection.isConnected)
    }

    @Test
    fun `sendChunk sends video when layout matches`() = runTest {
        connection.connect()
        
        val videoChunk = EncodedChunk(
            data = ByteArray(100),
            timestamp = 0L,
            isKeyFrame = true,
            layout = Layout.HORIZONTAL,
            isVideo = true
        )
        
        connection.sendChunk(videoChunk)
        
        verify { mockClient.writeVideoChunk(ByteArray(100), 0L) }
    }

    @Test
    fun `sendChunk sends audio when layout matches`() = runTest {
        connection.connect()
        
        val audioChunk = EncodedChunk(
            data = ByteArray(50),
            timestamp = 0L,
            isKeyFrame = false,
            layout = Layout.HORIZONTAL,
            isVideo = false
        )
        
        connection.sendChunk(audioChunk)
        
        verify { mockClient.writeAudioChunk(ByteArray(50), 0L) }
    }

    @Test
    fun `sendChunk ignores chunks with different layout`() = runTest {
        connection.connect()
        
        val verticalChunk = EncodedChunk(
            data = ByteArray(100),
            timestamp = 0L,
            isKeyFrame = true,
            layout = Layout.VERTICAL,
            isVideo = true
        )
        
        connection.sendChunk(verticalChunk)
        
        verify(exactly = 0) { mockClient.writeVideoChunk(any(), any()) }
        verify(exactly = 0) { mockClient.writeAudioChunk(any(), any()) }
    }

    @Test
    fun `sendChunk does nothing when not connected`() {
        val videoChunk = EncodedChunk(
            data = ByteArray(100),
            timestamp = 0L,
            isKeyFrame = true,
            layout = Layout.HORIZONTAL,
            isVideo = true
        )
        
        connection.sendChunk(videoChunk)
        
        verify(exactly = 0) { mockClient.writeVideoChunk(any(), any()) }
    }

    @Test
    fun `disconnect disconnects client and sets isConnected to false`() = runTest {
        connection.connect()
        assertTrue(connection.isConnected)
        
        connection.disconnect()
        
        verify { mockClient.disconnect() }
        assertFalse(connection.isConnected)
    }

    @Test
    fun `sendChunk sends both video and audio chunks`() = runTest {
        connection.connect()
        
        val videoChunk = EncodedChunk(
            data = ByteArray(100),
            timestamp = 0L,
            isKeyFrame = true,
            layout = Layout.HORIZONTAL,
            isVideo = true
        )
        val audioChunk = EncodedChunk(
            data = ByteArray(50),
            timestamp = 100L,
            isKeyFrame = false,
            layout = Layout.HORIZONTAL,
            isVideo = false
        )
        
        connection.sendChunk(videoChunk)
        connection.sendChunk(audioChunk)
        
        verify { mockClient.writeVideoChunk(ByteArray(100), 0L) }
        verify { mockClient.writeAudioChunk(ByteArray(50), 100L) }
    }
}
