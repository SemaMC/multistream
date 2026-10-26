package com.multistream.core.encoding

import android.view.Surface
import com.multistream.core.common.HORIZONTAL_CONFIG
import com.multistream.core.common.Layout
import com.multistream.core.common.LayoutConfig
import com.multistream.core.common.VERTICAL_CONFIG

/**
 * Output of [MultiLayoutCompositor]: both composition surfaces produced by the same pass.
 *
 * The two surfaces are independent render targets. In the production pipeline they are the input
 * surfaces of the two [VideoEncoder] instances, so everything drawn into them is what each layout
 * ends up encoding.
 */
data class CompositionResult(
    val horizontalSurface: Surface,
    val verticalSurface: Surface,
    val horizontalConfig: LayoutConfig = HORIZONTAL_CONFIG,
    val verticalConfig: LayoutConfig = VERTICAL_CONFIG
) {
    /** Returns the composition surface that belongs to [layout]. */
    fun surfaceFor(layout: Layout): Surface = when (layout) {
        Layout.HORIZONTAL -> horizontalSurface
        Layout.VERTICAL -> verticalSurface
    }

    /** Returns the layout configuration (size, fps, bitrate) that belongs to [layout]. */
    fun configFor(layout: Layout): LayoutConfig = when (layout) {
        Layout.HORIZONTAL -> horizontalConfig
        Layout.VERTICAL -> verticalConfig
    }
}
