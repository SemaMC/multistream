package com.multistream.core.encoding

import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioEncoderTest {
    
    private lateinit var encoder: AudioEncoder
    private lateinit var mockContext: android.content.Context
    
    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        encoder = AudioEncoder(mockContext)
    }
    
    @After
    fun tearDown() {
        encoder.stop()
        unmockkAll()
    }
    
    @Test
    fun `AudioEncoder can be created`() {
        assertNotNull(encoder)
    }
    
    @Test
    fun `stop does not throw exception when encoder is null`() {
        // Should not throw
        encoder.stop()
    }
}
