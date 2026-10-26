package com.multistream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
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
 * add/remove operations the screen drives.
 *
 * The flows derive from the manager's own [StateFlow], so [WidgetConfigScreen] stays in sync when
 * a widget changes anywhere in the app.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val widgetManager: WidgetLayoutManager
) : ViewModel() {

    val horizontalWidgets: StateFlow<List<Widget>> = widgetsIn(Layout.HORIZONTAL)
    val verticalWidgets: StateFlow<List<Widget>> = widgetsIn(Layout.VERTICAL)

    /** Adds a widget to [layout], generating its id. The caller validates the values. */
    fun addWidget(layout: Layout, url: String, x: Float, y: Float, width: Int, height: Int) {
        widgetManager.addWidget(
            Widget(
                id = UUID.randomUUID().toString(),
                url = url.trim(),
                position = WidgetPosition(x, y),
                size = WidgetSize(width, height),
                layout = layout
            )
        )
    }

    /** Removes the widget with [id] from whichever layout it belongs to. */
    fun removeWidget(id: String) {
        widgetManager.removeWidget(id)
    }

    private fun widgetsIn(layout: Layout): StateFlow<List<Widget>> =
        widgetManager.getAllWidgets()
            .map { widgets -> widgets.filter { it.layout == layout } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )
}
