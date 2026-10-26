package com.multistream.core.audio

import org.junit.Assert.*
import org.junit.Test

class AudioMixerTest {
    
    @Test
    fun `mix with empty sources returns empty array`() {
        val mixer = AudioMixer()
        val result = mixer.mix(emptyList())
        assertEquals(0, result.size)
    }
    
    @Test
    fun `mix with single source returns that source`() {
        val mixer = AudioMixer()
        val source = shortArrayOf(100, 200, 300)
        val result = mixer.mix(listOf(source))
        assertArrayEquals(source, result)
    }
    
    @Test
    fun `mix with two sources combines them correctly`() {
        val mixer = AudioMixer()
        val source1 = shortArrayOf(1000, 2000, 3000)
        val source2 = shortArrayOf(1000, 2000, 3000)
        
        val result = mixer.mix(listOf(source1, source2))
        
        assertEquals(3, result.size)
        // After normalization, values should be close to original
        assertTrue(result[0] > 0)
        assertTrue(result[1] > 0)
        assertTrue(result[2] > 0)
    }
    
    @Test
    fun `mix prevents clipping with normalization`() {
        val mixer = AudioMixer()
        val source1 = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE)
        val source2 = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE)
        val source3 = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE)
        
        val result = mixer.mix(listOf(source1, source2, source3))
        
        // Result should not exceed Short.MAX_VALUE
        for (sample in result) {
            assertTrue(sample <= Short.MAX_VALUE)
            assertTrue(sample >= Short.MIN_VALUE)
        }
    }
    
    @Test
    fun `mixGameAndMic combines game and mic audio`() {
        val mixer = AudioMixer()
        val gameAudio = shortArrayOf(1000, 2000, 3000)
        val micAudio = shortArrayOf(500, 1000, 1500)
        
        val result = mixer.mixGameAndMic(gameAudio, micAudio)
        
        assertEquals(3, result.size)
        assertTrue(result[0] > 0)
    }
    
    @Test
    fun `mix handles sources with different sizes`() {
        val mixer = AudioMixer()
        val source1 = shortArrayOf(100, 200, 300, 400, 500)
        val source2 = shortArrayOf(100, 200)
        
        val result = mixer.mix(listOf(source1, source2))
        
        // Result should have the size of the largest source
        assertEquals(5, result.size)
    }
    
    @Test
    fun `mix with silence passes audio unchanged`() {
        val mixer = AudioMixer()
        val audio = shortArrayOf(1000, 2000, 3000)
        val silence = shortArrayOf(0, 0, 0)
        
        val result = mixer.mix(listOf(audio, silence))
        
        assertEquals(3, result.size)
        // Should be close to original audio
        assertEquals(audio[0], result[0])
        assertEquals(audio[1], result[1])
        assertEquals(audio[2], result[2])
    }
}
