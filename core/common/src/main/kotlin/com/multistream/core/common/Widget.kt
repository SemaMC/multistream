package com.multistream.core.common

data class Widget(
    val id: String,
    val url: String,
    val position: WidgetPosition,
    val size: WidgetSize,
    val layout: Layout
)

data class WidgetPosition(val x: Float, val y: Float)
data class WidgetSize(val width: Int, val height: Int)
