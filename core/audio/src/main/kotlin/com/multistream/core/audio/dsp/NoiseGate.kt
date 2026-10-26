package com.multistream.core.audio.dsp

import kotlin.math.abs

class NoiseGate(
    private val thresholdDb: Float = -40f
) : AudioProcessor {
    
    private val thresholdLinear = dbToLinear(thresholdDb)
    
    override fun process(buffer: ShortArray): ShortArray {
        val result = ShortArray(buffer.size)
        
        for (i in buffer.indices) {
            val sample = buffer[i].toFloat()
            val absSample = abs(sample)
            
            // Apply noise gate: if below threshold, set to zero
            result[i] = if (absSample > thresholdLinear * Short.MAX_VALUE) {
                buffer[i]
            } else {
                0
            }
        }
        
        return result
    }
    
    private fun dbToLinear(db: Float): Float {
        return Math.pow(10.0, (db / 20.0)).toFloat()
    }
}
