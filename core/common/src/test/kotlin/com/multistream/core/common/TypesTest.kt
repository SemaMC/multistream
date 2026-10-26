package com.multistream.core.common

import org.junit.Assert.*
import org.junit.Test

class TypesTest {
    
    @Test
    fun `Layout enum has HORIZONTAL and VERTICAL values`() {
        assertEquals(2, Layout.values().size)
        assertNotNull(Layout.HORIZONTAL)
        assertNotNull(Layout.VERTICAL)
    }
    
    @Test
    fun `Platform enum has all required platforms`() {
        assertEquals(5, Platform.values().size)
        assertNotNull(Platform.TWITCH)
        assertNotNull(Platform.YOUTUBE)
        assertNotNull(Platform.KICK)
        assertNotNull(Platform.FACEBOOK)
        assertNotNull(Platform.CUSTOM)
    }
    
    @Test
    fun `EncodedChunk can be created with all fields`() {
        val chunk = EncodedChunk(
            data = ByteArray(100),
            timestamp = 12345L,
            isKeyFrame = true,
            layout = Layout.HORIZONTAL,
            isVideo = true
        )
        assertEquals(100, chunk.data.size)
        assertEquals(12345L, chunk.timestamp)
        assertTrue(chunk.isKeyFrame)
        assertEquals(Layout.HORIZONTAL, chunk.layout)
        assertTrue(chunk.isVideo)
    }
    
    @Test
    fun `EncodedChunk equality works correctly`() {
        val data = ByteArray(10) { it.toByte() }
        val chunk1 = EncodedChunk(data, 100L, true, Layout.HORIZONTAL, true)
        val chunk2 = EncodedChunk(data, 100L, true, Layout.HORIZONTAL, true)
        assertEquals(chunk1, chunk2)
    }
    
    @Test
    fun `HORIZONTAL_CONFIG has correct values`() {
        assertEquals(Layout.HORIZONTAL, HORIZONTAL_CONFIG.layout)
        assertEquals(1920, HORIZONTAL_CONFIG.width)
        assertEquals(1080, HORIZONTAL_CONFIG.height)
        assertEquals(60, HORIZONTAL_CONFIG.fps)
        assertEquals(6_000_000, HORIZONTAL_CONFIG.bitrate)
    }
    
    @Test
    fun `VERTICAL_CONFIG has correct values`() {
        assertEquals(Layout.VERTICAL, VERTICAL_CONFIG.layout)
        assertEquals(1080, VERTICAL_CONFIG.width)
        assertEquals(1920, VERTICAL_CONFIG.height)
        assertEquals(60, VERTICAL_CONFIG.fps)
        assertEquals(4_000_000, VERTICAL_CONFIG.bitrate)
    }
    
    @Test
    fun `PlatformCredentials converts to PlatformConfig`() {
        val credentials = PlatformCredentials(
            platform = Platform.TWITCH,
            rtmpUrl = "rtmp://live.twitch.tv/app/",
            streamKey = "live_123456"
        )
        val config = credentials.toConfig()
        
        assertEquals(Platform.TWITCH, config.platform)
        assertEquals("rtmp://live.twitch.tv/app/", config.rtmpUrl)
        assertEquals("live_123456", config.streamKey)
        assertEquals(6_000_000, config.bitrate)
    }
    
    @Test
    fun `PlatformCredentials converts to PlatformConfig with custom bitrate`() {
        val credentials = PlatformCredentials(
            platform = Platform.YOUTUBE,
            rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/",
            streamKey = "xxxx-xxxx-xxxx-xxxx"
        )
        val config = credentials.toConfig(bitrate = 8_000_000)
        
        assertEquals(8_000_000, config.bitrate)
    }
    
    @Test
    fun `Widget can be created with all fields`() {
        val widget = Widget(
            id = "chat-1",
            url = "https://example.com/chat",
            position = WidgetPosition(100f, 200f),
            size = WidgetSize(400, 300),
            layout = Layout.HORIZONTAL
        )
        
        assertEquals("chat-1", widget.id)
        assertEquals("https://example.com/chat", widget.url)
        assertEquals(100f, widget.position.x)
        assertEquals(200f, widget.position.y)
        assertEquals(400, widget.size.width)
        assertEquals(300, widget.size.height)
        assertEquals(Layout.HORIZONTAL, widget.layout)
    }
}
