package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the overlay widgets.
 *
 * The per-layout flows are derived synchronously on every change (instead of a `map` + `stateIn`
 * chain on an external scope), so a reader always observes the current list immediately after
 * [addWidget] or [removeWidget] returns.
 */
@Singleton
class WidgetLayoutManager @Inject constructor() {
    private val widgets = MutableStateFlow<List<Widget>>(emptyList())
    private val horizontalWidgets = MutableStateFlow<List<Widget>>(emptyList())
    private val verticalWidgets = MutableStateFlow<List<Widget>>(emptyList())

    fun addWidget(widget: Widget) {
        widgets.update { it + widget }
        refreshLayouts()
    }

    fun removeWidget(widgetId: String) {
        widgets.update { list -> list.filter { it.id != widgetId } }
        refreshLayouts()
    }

    fun getWidgetsForLayout(layout: Layout): StateFlow<List<Widget>> = when (layout) {
        Layout.HORIZONTAL -> horizontalWidgets.asStateFlow()
        Layout.VERTICAL -> verticalWidgets.asStateFlow()
    }

    fun getAllWidgets(): StateFlow<List<Widget>> = widgets.asStateFlow()

    private fun refreshLayouts() {
        val current = widgets.value
        horizontalWidgets.value = current.filter { it.layout == Layout.HORIZONTAL }
        verticalWidgets.value = current.filter { it.layout == Layout.VERTICAL }
    }
}
