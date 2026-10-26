package com.multistream.core.streaming

/**
 * RTMP client port. Mirrors the API defined in the plan (connect, writeVideoChunk,
 * writeAudioChunk, disconnect).
 *
 * NOTE: The artifact declared in the plan (`io.github.thibaultbee:rtmp-client:2.0.0`)
 * does not exist in Maven Central, and the alternatives are not usable with this
 * project's toolchain (see task-10-report.md):
 *  - io.github.thibaultbee.streampack:streampack-core/rtmp:3.2.1 -> compiled with
 *    Kotlin 2.4 metadata, unreadable by the 1.9.22 compiler.
 *  - io.github.thibaultbee:streampack-extension-rtmp:2.6.1 -> full-streamer pipeline,
 *    no low-level client.
 *  - io.antmedia:rtmp-client:3.2.0 -> FLV-tag level API (write ByteArray), no
 *    connect(url, key)/writeVideoChunk/writeAudioChunk surface.
 *
 * It is implemented locally so that RTMPConnection and its tests compile today;
 * Task 11 binds a real RTMP library behind this port (requires a decision about
 * updating the Kotlin toolchain).
 */
interface RtmpClient {
    fun connect(url: String, streamKey: String)
    fun writeVideoChunk(data: ByteArray, timestamp: Long)
    fun writeAudioChunk(data: ByteArray, timestamp: Long)
    fun disconnect()
}
