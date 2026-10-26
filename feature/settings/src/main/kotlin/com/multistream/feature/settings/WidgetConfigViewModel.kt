package com.multistream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import com.multistream.core.widgets.PanelMapper
import com.multistream.core.widgets.SnapHelper
import com.multistream.core.widgets.WidgetLayoutManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the widgets managed by [WidgetLayoutManager] split by layout and owns the
 * add/remove/move/resize operations the screen drives.
 *
 * The flows derive from the manager's own [StateFlow], so [WidgetConfigScreen] stays in sync when
 * a widget changes anywhere in the app.
 *
 * Positions and sizes travel in layout pixels here: the panel converts the on-screen drag before
 * calling [moveWidget] or [resizeWidget], so the ViewModel never needs the panel dimensions.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val widgetManager: WidgetLayoutManager
) : ViewModel() {

    val horizontalWidgets: StateFlow<List<Widget>> = widgetsIn(Layout.HORIZONTAL)
    val verticalWidgets: StateFlow<List<Widget>> = widgetsIn(Layout.VERTICAL)

    /** Adds a widget centred in [layout] with the default size, generating its id. */
    fun addWidget(layout: Layout, url: String = "") {
        val panel = PanelMapper.layoutSizeFor(layout)
        addWidget(
            layout = layout,
            url = url,
            position = WidgetPosition(
                x = (panel.width - DEFAULT_WIDTH) / 2f,
                y = (panel.height - DEFAULT_HEIGHT) / 2f
            ),
            size = WidgetSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        )
    }

    /**
     * Adds a widget to [layout] at explicit layout coordinates. The caller validates the values.
     *
     * Kept only so the current screen and tests keep compiling; the screen moves widgets through
     * the panel now, so new code should use `addWidget(layout, url)` and then [moveWidget].
     */
    @Deprecated(
        message = "Add a centred widget with addWidget(layout, url) and place it through the panel.",
        replaceWith = ReplaceWith("addWidget(layout, url)")
    )
    fun addWidget(layout: Layout, url: String, x: Float, y: Float, width: Int, height: Int) {
        addWidget(
            layout = layout,
            url = url,
            position = WidgetPosition(x, y),
            size = WidgetSize(width, height)
        )
    }

    /**
     * Moves the widget with [id] to [x], [y] in layout pixels, snapping to the panel guides when
     * [snapEnabled]. A missing [id] is a no-op.
     */
    fun moveWidget(id: String, x: Float, y: Float, snapEnabled: Boolean) {
        val widget = widgetManager.getAllWidgets().value.firstOrNull { it.id == id } ?: return
        val position = SnapHelper.snap(
            position = WidgetPosition(x, y),
            size = widget.size,
            layout = widget.layout,
            enabled = snapEnabled
        ).position
        widgetManager.updateWidget(id, position)
    }

    /**
     * Resizes the widget with [id] to [width] x [height] in layout pixels, clamped to
     * [PanelMapper.MIN_SIZE]. A missing [id] is a no-op and the position is left untouched.
     */
    fun resizeWidget(id: String, width: Int, height: Int) {
        if (widgetManager.getAllWidgets().value.none { it.id == id }) return
        widgetManager.updateWidget(id, size = PanelMapper.clampSize(width, height))
    }

    /** Replaces the URL of the widget with [id], leaving its position and size untouched. */
    fun setWidgetUrl(id: String, url: String) {
        widgetManager.updateWidget(id, url = url)
    }

    /** Removes the widget with [id] from whichever layout it belongs to. */
    fun removeWidget(id: String) {
        widgetManager.removeWidget(id)
    }

    private fun addWidget(layout: Layout, url: String, position: WidgetPosition, size: WidgetSize) {
        widgetManager.addWidget(
            Widget(
                id = UUID.randomUUID().toString(),
                url = url.trim(),
                position = position,
                size = size,
                layout = layout
            )
        )
    }

    private fun widgetsIn(layout: Layout): StateFlow<List<Widget>> =
        widgetManager.getAllWidgets()
            .map { widgets -> widgets.filter { it.layout == layout } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    private companion object {
        const val DEFAULT_WIDTH = 480
        const val DEFAULT_HEIGHT = 270
    }
}
