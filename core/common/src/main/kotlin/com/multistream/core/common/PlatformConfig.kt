package com.multistream.core.common

data class PlatformConfig(
    val platform: Platform,
    val rtmpUrl: String,
    val streamKey: String,
    val bitrate: Int
)
