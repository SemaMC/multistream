package com.multistream.core.streaming

/**
 * Taxonomy of the RTMP failures the streaming pipeline can surface.
 *
 * A sealed class (instead of an enum) lets each failure carry its own message while
 * the `when` in [ErrorHandler] stays exhaustive: a new failure forces every handler
 * to decide whether it is retryable.
 */
sealed class RTMPError(message: String) : Exception(message) {
    object INVALID_STREAM_KEY : RTMPError("Invalid stream key")
    object INGEST_CONGESTION : RTMPError("Ingest congestion")
    object CONNECTION_CLOSED : RTMPError("Connection closed")
    object AUTH_EXPIRED : RTMPError("Authentication expired")
    object TOO_FREQUENT : RTMPError("Too many requests")
    object NETWORK_UNAVAILABLE : RTMPError("Network unavailable")
    object RTMP_HANDSHAKE_FAILED : RTMPError("RTMP handshake failed")
    object FLV_MUX_ERROR : RTMPError("FLV muxing error")
    object PROJECTION_REVOKED : RTMPError("Media projection revoked")
}
