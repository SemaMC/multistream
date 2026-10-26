package com.multistream.core.encoding

/**
 * Interface for propagating sequence header changes to streaming connections.
 * Implemented by RTMPMultiStreamer in :core:streaming module.
 */
interface SequenceHeaderPropagator {
    fun propagateSequenceHeaderChange(header: ByteArray)
}
