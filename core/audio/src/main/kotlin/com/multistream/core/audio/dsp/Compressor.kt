package com.multistream.core.audio.dsp

import kotlin.math.abs

class Compressor(
    private val thresholdDb: Float = -20f,
    private val ratio: Float = 4f,
    private val attackMs: Float = 10f,
    private val releaseMs: Float = 100f,
    private val sampleRate: Int = 44100
) : AudioProcessor {
    
    private val threshold = dbToLinear(thresholdDb)
    private val attackCoeff = Math.exp(-1.0 / (attackMs * sampleRate / 1000.0)).toFloat()
    private val releaseCoeff = Math.exp(-1.0 / (releaseMs * sampleRate / 1000.0)).toFloat()
    
    private var envelope = 0f
    
    override fun process(buffer: ShortArray): ShortArray {
        val result = ShortArray(buffer.size)
        
        for (i in buffer.indices) {
            val input = buffer[i].toFloat() / Short.MAX_VALUE
            val inputAbs = abs(input)
            
            // Update envelope
            envelope = if (inputAbs > envelope) {
                attackCoeff * envelope + (1 - attackCoeff) * inputAbs
            } else {
                releaseCoeff * envelope + (1 - releaseCoeff) * inputAbs
            }
            
            // Calculate gain reduction
            val gainReduction = if (envelope > threshold) {
                val overDb = linearToDb(envelope) - linearToDb(threshold)
                val compressedOverDb = overDb / ratio
                val gainDb = compressedOverDb - overDb
                dbToLinear(gainDb)
            } else {
                1f
            }
            
            // Apply gain
            val output = input * gainReduction
            result[i] = (output * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        
        return result
    }
    
    private fun dbToLinear(db: Float): Float {
        return Math.pow(10.0, (db / 20.0)).toFloat()
    }
    
    private fun linearToDb(linear: Float): Float {
        return 20 * Math.log10(linear.toDouble()).toFloat()
    }
}
