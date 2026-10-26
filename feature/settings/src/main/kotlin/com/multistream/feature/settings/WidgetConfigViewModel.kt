package com.multistream.feature.settings

import androidx.lifecycle.ViewModel
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.widgets.WidgetLayoutManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the widgets managed by [WidgetLayoutManager] split by layout.
 *
 * The flows are the manager's own [StateFlow]s, so [WidgetConfigScreen] stays in sync when a
 * widget is added or removed elsewhere in the app.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val widgetManager: WidgetLayoutManager
) : ViewModel() {

    val horizontalWidgets: StateFlow<List<Widget>> =
        widgetManager.getWidgetsForLayout(Layout.HORIZONTAL)
    val verticalWidgets: StateFlow<List<Widget>> =
        widgetManager.getWidgetsForLayout(Layout.VERTICAL)
}
