package com.multistream.core.common

data class PlatformCredentials(
    val platform: Platform,
    val rtmpUrl: String,
    val streamKey: String
)

fun PlatformCredentials.toConfig(bitrate: Int = 6_000_000): PlatformConfig =
    PlatformConfig(platform, rtmpUrl, streamKey, bitrate)
