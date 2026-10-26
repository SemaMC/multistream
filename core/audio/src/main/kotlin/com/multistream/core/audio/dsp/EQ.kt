package com.multistream.core.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class EQBand(
    val frequency: Float,  // Hz
    val gainDb: Float,     // dB
    val q: Float = 1.0f    // Quality factor
)

class EQ(
    private val bands: List<EQBand> = emptyList(),
    private val sampleRate: Int = 44100
) : AudioProcessor {
    
    private val filters = bands.map { band ->
        BiquadFilter.createPeakingEQ(band.frequency, band.gainDb, band.q, sampleRate)
    }
    
    override fun process(buffer: ShortArray): ShortArray {
        var result = buffer.map { it.toFloat() }.toFloatArray()
        
        // Apply each EQ band
        for (filter in filters) {
            result = filter.process(result)
        }
        
        // Convert back to ShortArray
        return ShortArray(result.size) { i ->
            result[i].toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

class BiquadFilter(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float
) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f
    
    fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        
        for (i in input.indices) {
            val x0 = input[i]
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            
            output[i] = y0
            
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        
        return output
    }
    
    companion object {
        fun createPeakingEQ(frequency: Float, gainDb: Float, q: Float, sampleRate: Int): BiquadFilter {
            val a = Math.pow(10.0, (gainDb / 40.0)).toFloat()
            val w0 = 2 * PI.toFloat() * frequency / sampleRate
            val alpha = sin(w0) / (2 * q)
            
            val b0 = 1 + alpha * a
            val b1 = -2 * cos(w0)
            val b2 = 1 - alpha * a
            val a0 = 1 + alpha / a
            val a1 = -2 * cos(w0)
            val a2 = 1 - alpha / a
            
            return BiquadFilter(
                b0 / a0, b1 / a0, b2 / a0,
                a1 / a0, a2 / a0
            )
        }
    }
}
