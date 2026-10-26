package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import javax.inject.Singleton

@Singleton
class WidgetLayoutManager @Inject constructor() {
    private val widgets = MutableStateFlow<List<Widget>>(emptyList())

    fun addWidget(widget: Widget) {
        widgets.update { it + widget }
    }

    fun removeWidget(widgetId: String) {
        widgets.update { list -> list.filter { it.id != widgetId } }
    }

    fun getWidgetsForLayout(layout: Layout): StateFlow<List<Widget>> {
        return widgets.map { list -> list.filter { it.layout == layout } }.stateIn(
            scope = CoroutineScope(Dispatchers.Default),
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
    }

    fun getAllWidgets(): StateFlow<List<Widget>> = widgets
}
