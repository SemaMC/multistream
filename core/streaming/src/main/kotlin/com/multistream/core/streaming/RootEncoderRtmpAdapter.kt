package com.multistream.core.streaming

import android.media.MediaCodec
import com.pedro.common.ConnectChecker
import com.pedro.rtmp.rtmp.RtmpClient as PedroRtmpClient
import java.nio.ByteBuffer

/**
 * Adapter that bridges our RtmpClient interface to RootEncoder's RtmpClient.
 * 
 * RootEncoder is a mature, actively maintained RTMP library for Android (pedroSG94/RootEncoder)
 * written in Kotlin, compatible with our toolchain (Kotlin 1.9.22, minSdk 29, targetSdk 36).
 * 
 * This adapter handles:
 * - URL composition: combines base URL + stream key into RootEncoder's expected format
 * - Data conversion: transforms ByteArray + timestamp into ByteBuffer + MediaCodec.BufferInfo
 * - Connection state: implements ConnectChecker to track connection lifecycle
 * - Thread safety: all operations delegate to RootEncoder which is thread-safe
 */
class RootEncoderRtmpAdapter : RtmpClient {
    
    private lateinit var client: PedroRtmpClient
    private var isConnected = false
    
    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            // Connection attempt started
        }
        
        override fun onConnectionFailed(reason: String) {
            isConnected = false
            // Connection failed - could be due to invalid URL, network issues, or server rejection
            throw RuntimeException("RTMP connection failed: $reason")
        }
        
        override fun onNewBitrate(bitrate: Long) {
            // Bitrate update - ignored for now as we use fixed bitrate encoding
        }
        
        override fun onConnectionSuccess() {
            isConnected = true
            // Successfully connected and ready to stream
        }
        
        override fun onDisconnect() {
            isConnected = false
            // Disconnected from server
        }
        
        override fun onAuthError() {
            // Authentication failed - invalid stream key or credentials
            throw RuntimeException("RTMP authentication failed")
        }
        
        override fun onAuthSuccess() {
            // Authentication successful
        }
    }
    
    override fun connect(url: String, streamKey: String) {
        client = RtmpClient(connectChecker)
        
        // RootEncoder expects full URL with stream key appended
        // Format: rtmp://host/app/streamKey
        val fullUrl = if (url.endsWith("/")) {
            "$url$streamKey"
        } else {
            "$url/$streamKey"
        }
        
        client.connect(fullUrl)
        
        // Wait a bit for connection to establish
        // In production, this should be handled via callbacks or coroutines
        Thread.sleep(500)
        
        if (!isConnected) {
            throw RuntimeException("Failed to establish RTMP connection")
        }
    }
    
    override fun writeVideoChunk(data: ByteArray, timestamp: Long) {
        if (!isConnected) return
        
        val buffer = ByteBuffer.wrap(data)
        val info = MediaCodec.BufferInfo().apply {
            set(0, data.size, timestamp, MediaCodec.BUFFER_FLAG_KEY_FRAME)
        }
        client.sendVideo(buffer, info)
    }
    
    override fun writeAudioChunk(data: ByteArray, timestamp: Long) {
        if (!isConnected) return
        
        val buffer = ByteBuffer.wrap(data)
        val info = MediaCodec.BufferInfo().apply {
            set(0, data.size, timestamp, 0)
        }
        client.sendAudio(buffer, info)
    }
    
    override fun disconnect() {
        if (::client.isInitialized) {
            client.disconnect()
        }
        isConnected = false
    }
}
