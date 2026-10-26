package com.multistream.core.streaming

import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RTMPMultiStreamerTest {

    private class FakeRtmpClient(private val failFirstConnect: Boolean) : RtmpClient {
        var connectAttempts = 0
            private set
        var isConnected = false
            private set
        var disconnectCalled = false
            private set
        val videoChunks = mutableListOf<Pair<ByteArray, Long>>()
        val audioChunks = mutableListOf<Pair<ByteArray, Long>>()

        override fun connect(url: String, streamKey: String) {
            connectAttempts++
            if (failFirstConnect && connectAttempts == 1) {
                throw IOException("connection refused")
            }
            isConnected = true
        }

        override fun writeVideoChunk(data: ByteArray, timestamp: Long) {
            videoChunks += data to timestamp
        }

        override fun writeAudioChunk(data: ByteArray, timestamp: Long) {
            audioChunks += data to timestamp
        }

        override fun disconnect() {
            disconnectCalled = true
            isConnected = false
        }
    }

    private class FixedLayoutRouter(
        private val vertical: Set<Platform> = emptySet()
    ) : LayoutRouter {
        override fun getLayout(platform: Platform): Layout =
            if (platform in vertical) Layout.VERTICAL else Layout.HORIZONTAL
    }

    /**
     * Creates real [RTMPConnection]s around [FakeRtmpClient]s. The first
     * [failuresBeforeSuccess] clients of a platform fail their first connect, so the
     * retry path is exercised end to end.
     */
    private class RecordingFactory(
        private val failuresBeforeSuccess: Map<Platform, Int> = emptyMap()
    ) : RTMPConnectionFactory {
        private val clientsByPlatform = mutableMapOf<Platform, MutableList<FakeRtmpClient>>()

        override fun create(config: PlatformConfig, layout: Layout): RTMPConnection {
            val clients = clientsByPlatform.getOrPut(config.platform) { mutableListOf() }
            val failures = failuresBeforeSuccess[config.platform] ?: 0
            val client = FakeRtmpClient(failFirstConnect = clients.size < failures)
            clients += client
            return RTMPConnection(config, layout) { client }
        }

        fun clients(platform: Platform): List<FakeRtmpClient> =
            clientsByPlatform[platform].orEmpty()

        fun onlyClient(platform: Platform): FakeRtmpClient = clients(platform).single()
    }

    private fun config(platform: Platform) = PlatformConfig(
        platform = platform,
        rtmpUrl = "rtmp://live.example.com/app",
        streamKey = "key-${platform.name}",
        bitrate = 6_000_000
    )

    private fun chunk(layout: Layout, tag: Int, isVideo: Boolean = true) = EncodedChunk(
        data = byteArrayOf(tag.toByte()),
        timestamp = tag.toLong(),
        isKeyFrame = true,
        layout = layout,
        isVideo = isVideo
    )

    @Test
    fun `routes chunks to correct layout connections`() = runTest {
        val router = FixedLayoutRouter(vertical = setOf(Platform.CUSTOM))
        val factory = RecordingFactory()
        val streamer = RTMPMultiStreamer(router, RetryPolicy(), factory)

        streamer.connect(listOf(config(Platform.TWITCH), config(Platform.CUSTOM)))

        val horizontal = chunk(Layout.HORIZONTAL, tag = 1)
        val vertical = chunk(Layout.VERTICAL, tag = 2)
        streamer.send(horizontal)
        streamer.send(vertical)

        val twitch = factory.onlyClient(Platform.TWITCH)
        val custom = factory.onlyClient(Platform.CUSTOM)

        assertEquals(1, twitch.videoChunks.size)
        assertArrayEquals(horizontal.data, twitch.videoChunks.single().first)
        assertEquals(1, custom.videoChunks.size)
        assertArrayEquals(vertical.data, custom.videoChunks.single().first)
    }

    @Test
    fun `handles multiple platforms simultaneously`() = runTest {
        val router = FixedLayoutRouter()
        val factory = RecordingFactory()
        val streamer = RTMPMultiStreamer(router, RetryPolicy(), factory)

        streamer.connect(listOf(config(Platform.TWITCH), config(Platform.YOUTUBE)))

        val video = chunk(Layout.HORIZONTAL, tag = 10)
        val audio = chunk(Layout.HORIZONTAL, tag = 11, isVideo = false)
        streamer.send(video)
        streamer.send(audio)

        for (platform in listOf(Platform.TWITCH, Platform.YOUTUBE)) {
            val client = factory.onlyClient(platform)
            assertEquals(1, client.videoChunks.size)
            assertArrayEquals(video.data, client.videoChunks.single().first)
            assertEquals(1, client.audioChunks.size)
            assertArrayEquals(audio.data, client.audioChunks.single().first)
        }
        assertEquals(2, streamer.connectionCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reconnection retries with backoff`() = runTest {
        val factory = RecordingFactory(
            failuresBeforeSuccess = mapOf(Platform.TWITCH to 2)
        )
        val retryPolicy = RetryPolicy(
            maxAttempts = 5,
            backoff = ExponentialBackoff(initial = 100, max = 1_000)
        )
        val streamer = RTMPMultiStreamer(FixedLayoutRouter(), retryPolicy, factory)

        streamer.connect(listOf(config(Platform.TWITCH)))

        val clients = factory.clients(Platform.TWITCH)
        assertEquals(3, clients.size)
        assertFalse(clients[0].isConnected)
        assertFalse(clients[1].isConnected)
        assertTrue(clients[2].isConnected)
        assertTrue(streamer.getConnection(Platform.TWITCH)!!.isConnected)
        assertEquals(1, streamer.connectionCount)
        // 1st retry waited 100ms, 2nd waited 200ms (exponential backoff).
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun `getHorizontalConnectionCount returns correct count`() = runTest {
        val router = FixedLayoutRouter(vertical = setOf(Platform.CUSTOM))
        val streamer = RTMPMultiStreamer(router, RetryPolicy(), RecordingFactory())

        streamer.connect(
            listOf(
                config(Platform.TWITCH),
                config(Platform.YOUTUBE),
                config(Platform.CUSTOM)
            )
        )

        assertEquals(2, streamer.getHorizontalConnectionCount())
    }

    @Test
    fun `getVerticalConnectionCount returns correct count`() = runTest {
        val router = FixedLayoutRouter(vertical = setOf(Platform.CUSTOM))
        val streamer = RTMPMultiStreamer(router, RetryPolicy(), RecordingFactory())

        streamer.connect(
            listOf(
                config(Platform.TWITCH),
                config(Platform.CUSTOM)
            )
        )

        assertEquals(1, streamer.getVerticalConnectionCount())
    }

    @Test
    fun `disconnectAll clears all connections`() = runTest {
        val factory = RecordingFactory()
        val streamer = RTMPMultiStreamer(FixedLayoutRouter(), RetryPolicy(), factory)
        streamer.connect(listOf(config(Platform.TWITCH), config(Platform.YOUTUBE)))

        streamer.disconnectAll()

        assertEquals(0, streamer.connectionCount)
        assertEquals(0, streamer.getHorizontalConnectionCount())
        assertNull(streamer.getConnection(Platform.TWITCH))
        assertTrue(factory.onlyClient(Platform.TWITCH).disconnectCalled)
        assertTrue(factory.onlyClient(Platform.YOUTUBE).disconnectCalled)
    }
}
