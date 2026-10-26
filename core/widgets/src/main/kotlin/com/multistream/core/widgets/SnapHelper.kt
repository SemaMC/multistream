package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import kotlin.math.abs

/** Guides the widget can snap to inside a layout panel. */
enum class Guideline { LEFT_EDGE, RIGHT_EDGE, H_CENTER, TOP_EDGE, BOTTOM_EDGE, V_CENTER }

/** Snap outcome: the corrected position and the guides the widget is aligned to. */
data class SnapResult(val position: WidgetPosition, val guides: Set<Guideline>)

/**
 * Pure magnetic snap against the layout panel edges and centres.
 *
 * Each guide is evaluated independently in layout pixels: when the distance from the widget to
 * the guide is within [TOLERANCE] the position is corrected to that guide. A widget already on a
 * guide keeps its position and still declares the guide. Disabled snapping returns the position
 * untouched and no guides.
 */
object SnapHelper {
    const val TOLERANCE = 12f

    fun snap(
        position: WidgetPosition,
        size: WidgetSize,
        layout: Layout,
        enabled: Boolean
    ): SnapResult {
        if (!enabled) return SnapResult(position, emptySet())

        val panel = PanelMapper.layoutSizeFor(layout)
        val guides = mutableSetOf<Guideline>()
        var x = position.x
        var y = position.y

        if (abs(position.x) <= TOLERANCE) {
            x = 0f
            guides += Guideline.LEFT_EDGE
        }
        if (abs(position.x + size.width - panel.width) <= TOLERANCE) {
            x = (panel.width - size.width).toFloat()
            guides += Guideline.RIGHT_EDGE
        }
        if (abs(position.x + size.width / 2f - panel.width / 2f) <= TOLERANCE) {
            x = panel.width / 2f - size.width / 2f
            guides += Guideline.H_CENTER
        }
        if (abs(position.y) <= TOLERANCE) {
            y = 0f
            guides += Guideline.TOP_EDGE
        }
        if (abs(position.y + size.height - panel.height) <= TOLERANCE) {
            y = (panel.height - size.height).toFloat()
            guides += Guideline.BOTTOM_EDGE
        }
        if (abs(position.y + size.height / 2f - panel.height / 2f) <= TOLERANCE) {
            y = panel.height / 2f - size.height / 2f
            guides += Guideline.V_CENTER
        }

        return SnapResult(WidgetPosition(x, y), guides)
    }
}
