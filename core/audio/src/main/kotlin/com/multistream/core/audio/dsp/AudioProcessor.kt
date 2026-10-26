package com.multistream.core.audio.dsp

interface AudioProcessor {
    fun process(buffer: ShortArray): ShortArray
}
