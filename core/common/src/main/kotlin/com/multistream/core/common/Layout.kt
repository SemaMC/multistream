package com.multistream.core.common

enum class Layout {
    HORIZONTAL,  // 1920x1080, 16:9 for Twitch/YouTube/Kick/Facebook
    VERTICAL     // 1080x1920, 9:16 prepared for TikTok
}

data class LayoutConfig(
    val layout: Layout,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
)

val HORIZONTAL_CONFIG = LayoutConfig(Layout.HORIZONTAL, 1920, 1080, 60, 6_000_000)
val VERTICAL_CONFIG = LayoutConfig(Layout.VERTICAL, 1080, 1920, 60, 4_000_000)
