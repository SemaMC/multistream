package com.multistream.core.encoding

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEncoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var encoder: MediaCodec? = null
    private var presentationTimeUs = 0L
    
    fun configure(
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        bitRate: Int = 128000,
        layout: Layout = Layout.HORIZONTAL
    ) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder?.start()
    }
    
    fun encode(pcmData: ShortArray, layout: Layout): Flow<EncodedChunk> = flow {
        val encoder = this@AudioEncoder.encoder ?: return@flow
        
        val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            
            // Convert ShortArray to ByteArray
            val byteBuffer = java.nio.ByteBuffer.allocate(pcmData.size * 2)
            byteBuffer.asShortBuffer().put(pcmData)
            val byteArray = byteBuffer.array()
            
            inputBuffer.put(byteArray)
            encoder.queueInputBuffer(
                inputBufferIndex,
                0,
                byteArray.size,
                presentationTimeUs,
                0
            )
            
            presentationTimeUs += (pcmData.size * 1000000L) / 44100 / 2
        }
        
        val bufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        
        while (outputBufferIndex >= 0) {
            val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
            
            if (outputBuffer != null && bufferInfo.size > 0) {
                val encodedData = ByteArray(bufferInfo.size)
                outputBuffer.get(encodedData)
                outputBuffer.clear()
                
                emit(
                    EncodedChunk(
                        data = encodedData,
                        timestamp = bufferInfo.presentationTimeUs,
                        isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                        layout = layout,
                        isVideo = false
                    )
                )
            }
            
            encoder.releaseOutputBuffer(outputBufferIndex, false)
            outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        }
    }
    
    fun stop() {
        encoder?.stop()
        encoder?.release()
        encoder = null
        presentationTimeUs = 0L
    }
    
    companion object {
        private const val TIMEOUT_US = 10000L
    }
}
