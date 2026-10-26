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
     * Factory of the [RtmpClient] port. The default throws because this project does
     * not yet bind a real RTMP library (see RtmpClient.kt); injectable so that the
     * tests use a MockK mock and Task 11 wires the real client.
     */
    private val clientFactory: () -> RtmpClient = {
        error(
            "No real RTMP client is bound yet. Inject a clientFactory " +
                "with the RTMP library chosen in Task 11."
        )
    }
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
