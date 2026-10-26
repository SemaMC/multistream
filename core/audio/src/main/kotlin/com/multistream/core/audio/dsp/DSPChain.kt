package com.multistream.core.audio.dsp

class DSPChain {
    private val processors = mutableListOf<AudioProcessor>()
    
    fun addProcessor(processor: AudioProcessor) {
        processors.add(processor)
    }
    
    fun process(buffer: ShortArray): ShortArray {
        var result = buffer
        for (processor in processors) {
            result = processor.process(result)
        }
        return result
    }
    
    fun clear() {
        processors.clear()
    }
    
    val processorCount: Int
        get() = processors.size
}
