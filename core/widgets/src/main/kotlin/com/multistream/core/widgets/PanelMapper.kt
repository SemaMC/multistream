package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import kotlin.math.roundToInt

/** Size in touch pixels of the on-screen panel that previews the layout. */
data class PanelDims(val width: Int, val height: Int)

/**
 * Pure conversions between panel (touch) pixels and layout pixels.
 *
 * Widgets persist their position/size in layout pixels, while the editor works with the
 * on-screen panel. Positions stay fractional in both directions so a drag round trip does not
 * drift; only [toLayoutSize] rounds, because a widget size is stored as whole layout pixels.
 */
object PanelMapper {
    const val MIN_SIZE = 100

    fun layoutSizeFor(layout: Layout): WidgetSize = when (layout) {
        Layout.HORIZONTAL -> WidgetSize(1920, 1080)
        Layout.VERTICAL -> WidgetSize(1080, 1920)
    }

    fun toLayoutPosition(
        touchX: Float,
        touchY: Float,
        panel: PanelDims,
        layout: Layout
    ): WidgetPosition {
        val layoutSize = layoutSizeFor(layout)
        return WidgetPosition(
            x = touchX / panel.width * layoutSize.width,
            y = touchY / panel.height * layoutSize.height
        )
    }

    fun toTouchPosition(
        position: WidgetPosition,
        panel: PanelDims,
        layout: Layout
    ): WidgetPosition {
        val layoutSize = layoutSizeFor(layout)
        return WidgetPosition(
            x = position.x / layoutSize.width * panel.width,
            y = position.y / layoutSize.height * panel.height
        )
    }

    fun toLayoutSize(
        touchWidth: Float,
        touchHeight: Float,
        panel: PanelDims,
        layout: Layout
    ): WidgetSize {
        val layoutSize = layoutSizeFor(layout)
        return WidgetSize(
            width = (touchWidth / panel.width * layoutSize.width).roundToInt(),
            height = (touchHeight / panel.height * layoutSize.height).roundToInt()
        )
    }

    fun clampSize(width: Int, height: Int): WidgetSize = WidgetSize(
        width = width.coerceAtLeast(MIN_SIZE),
        height = height.coerceAtLeast(MIN_SIZE)
    )
}
