package com.multistream.core.streaming

import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import com.multistream.core.common.PlatformConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RTMPConnection(
    val config: PlatformConfig,
    val layout: Layout,
    /**
     * Factory of the [RtmpClient]. Defaults to [RootEncoderRtmpAdapter] which uses RootEncoder
     * (pedroSG94/RootEncoder), a mature Kotlin RTMP library compatible with our toolchain.
     * Injectable for testing with mocks.
     */
    private val clientFactory: () -> RtmpClient = { RootEncoderRtmpAdapter() }
) {
    private var client: RtmpClient? = null
    var isConnected = false
        private set

    suspend fun connect() = withContext(Dispatchers.IO) {
        client = clientFactory().apply {
            connect(config.rtmpUrl, config.streamKey)
        }
        isConnected = true
    }

    fun sendChunk(chunk: EncodedChunk) {
        if (isConnected && chunk.layout == layout) {
            if (chunk.isVideo) {
                client?.writeVideoChunk(chunk.data, chunk.timestamp)
            } else {
                client?.writeAudioChunk(chunk.data, chunk.timestamp)
            }
        }
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        isConnected = false
    }
}
