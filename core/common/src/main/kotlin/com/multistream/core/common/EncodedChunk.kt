package com.multistream.core.common

data class EncodedChunk(
    val data: ByteArray,
    val timestamp: Long,
    val isKeyFrame: Boolean,
    val layout: Layout,
    val isVideo: Boolean = true  // true for video, false for audio
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedChunk) return false
        return data.contentEquals(other.data) &&
               timestamp == other.timestamp &&
               isKeyFrame == other.isKeyFrame &&
               layout == other.layout &&
               isVideo == other.isVideo
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + layout.hashCode()
        result = 31 * result + isVideo.hashCode()
        return result
    }
}
