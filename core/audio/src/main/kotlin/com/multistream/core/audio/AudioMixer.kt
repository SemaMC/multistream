package com.multistream.core.audio

class AudioMixer {
    
    fun mix(sources: List<ShortArray>): ShortArray {
        if (sources.isEmpty()) return ShortArray(0)
        if (sources.size == 1) return sources[0]
        
        val bufferSize = sources.maxOf { it.size }
        val mixed = FloatArray(bufferSize)
        
        // Sum all sources
        for (source in sources) {
            for (i in source.indices) {
                mixed[i] += source[i].toFloat() / Short.MAX_VALUE
            }
        }
        
        // Normalize to prevent clipping
        val maxAmplitude = mixed.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val normalizationFactor = if (maxAmplitude > 1f) 1f / maxAmplitude else 1f
        
        // Convert back to ShortArray
        return ShortArray(bufferSize) { i ->
            val normalized = mixed[i] * normalizationFactor
            (normalized * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
    
    fun mixGameAndMic(gameAudio: ShortArray, micAudio: ShortArray): ShortArray {
        return mix(listOf(gameAudio, micAudio))
    }
}
