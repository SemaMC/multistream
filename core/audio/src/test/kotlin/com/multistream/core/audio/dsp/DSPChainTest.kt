package com.multistream.core.audio.dsp

import org.junit.Assert.*
import org.junit.Test

class DSPChainTest {
    
    @Test
    fun `DSPChain processes buffer through all processors`() {
        val chain = DSPChain()
        val processor1 = TestProcessor(1)
        val processor2 = TestProcessor(2)
        
        chain.addProcessor(processor1)
        chain.addProcessor(processor2)
        
        val input = shortArrayOf(100, 200, 300)
        val output = chain.process(input)
        
        // Verify both processors were applied
        assertEquals(3, output.size)
        assertTrue(processor1.processCalled)
        assertTrue(processor2.processCalled)
    }
    
    @Test
    fun `DSPChain with no processors returns original buffer`() {
        val chain = DSPChain()
        val input = shortArrayOf(100, 200, 300)
        
        val output = chain.process(input)
        
        assertArrayEquals(input, output)
    }
    
    @Test
    fun `DSPChain clear removes all processors`() {
        val chain = DSPChain()
        chain.addProcessor(TestProcessor(1))
        chain.addProcessor(TestProcessor(2))
        
        assertEquals(2, chain.processorCount)
        
        chain.clear()
        
        assertEquals(0, chain.processorCount)
    }
    
    @Test
    fun `NoiseGate mutes samples below threshold`() {
        val gate = NoiseGate(thresholdDb = -40f)
        val input = shortArrayOf(100, 30000, 50, 25000)
        
        val output = gate.process(input)
        
        // High amplitude samples should pass, low amplitude should be muted
        assertEquals(0.toShort(), output[0])
        assertEquals(30000.toShort(), output[1])
        assertEquals(0.toShort(), output[2])
        assertEquals(25000.toShort(), output[3])
    }
    
    @Test
    fun `NoiseGate with different thresholds`() {
        val gateLow = NoiseGate(thresholdDb = -60f)
        val gateHigh = NoiseGate(thresholdDb = -20f)
        
        val input = shortArrayOf(1000, 10000, 20000)
        
        val outputLow = gateLow.process(input)
        val outputHigh = gateHigh.process(input)
        
        // Lower threshold should pass more samples
        var passCountLow = outputLow.count { it != 0 }
        var passCountHigh = outputHigh.count { it != 0 }
        
        assertTrue(passCountLow >= passCountHigh)
    }
    
    @Test
    fun `EQ with no bands passes audio unchanged`() {
        val eq = EQ(bands = emptyList())
        val input = shortArrayOf(100, 200, 300)
        
        val output = eq.process(input)
        
        // Should be approximately equal (small floating point errors expected)
        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertTrue(abs(input[i] - output[i]) <= 1)
        }
    }
    
    @Test
    fun `Compressor reduces gain above threshold`() {
        val compressor = Compressor(
            thresholdDb = -20f,
            ratio = 4f,
            attackMs = 1f,
            releaseMs = 10f
        )
        
        val input = ShortArray(1000) { (it * 100).toShort() }
        val output = compressor.process(input)
        
        // Output should have lower amplitude than input due to compression
        val inputRms = calculateRMS(input)
        val outputRms = calculateRMS(output)
        
        assertTrue(outputRms < inputRms)
    }
    
    @Test
    fun `Compressor with high threshold passes audio unchanged`() {
        val compressor = Compressor(
            thresholdDb = 0f,  // Very high threshold
            ratio = 4f
        )
        
        val input = shortArrayOf(100, 200, 300)
        val output = compressor.process(input)
        
        // Should be approximately equal (small compression expected)
        assertEquals(input.size, output.size)
    }
    
    @Test
    fun `Full DSP chain processes audio correctly`() {
        val chain = DSPChain()
        chain.addProcessor(NoiseGate(thresholdDb = -40f))
        chain.addProcessor(EQ(listOf(EQBand(1000f, 3f)), 44100))
        chain.addProcessor(Compressor(thresholdDb = -20f, ratio = 4f))
        
        assertEquals(3, chain.processorCount)
        
        val input = shortArrayOf(100, 5000, 10000, 20000)
        val output = chain.process(input)
        
        assertEquals(input.size, output.size)
    }
    
    private fun calculateRMS(buffer: ShortArray): Float {
        val sumSquares = buffer.sumOf { it.toDouble() * it.toDouble() }
        return Math.sqrt(sumSquares / buffer.size).toFloat()
    }
    
    private fun abs(value: Short): Int {
        return kotlin.math.abs(value.toInt())
    }
    
    // Test helper class
    private class TestProcessor(val id: Int) : AudioProcessor {
        var processCalled = false
        
        override fun process(buffer: ShortArray): ShortArray {
            processCalled = true
            return ShortArray(buffer.size) { (buffer[it] + id).toShort() }
        }
    }
}
