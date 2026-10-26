package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutManagerTest {

    @Test
    fun `WidgetLayoutManager maintains separate widget lists per layout as StateFlow`() = runTest {
        val manager = WidgetLayoutManager()
        val horizontalWidget = Widget(
            "1",
            "https://example.com/h",
            WidgetPosition(0f, 0f),
            WidgetSize(400, 300),
            Layout.HORIZONTAL
        )
        val verticalWidget = Widget(
            "2",
            "https://example.com/v",
            WidgetPosition(0f, 0f),
            WidgetSize(300, 400),
            Layout.VERTICAL
        )

        manager.addWidget(horizontalWidget)
        manager.addWidget(verticalWidget)

        val horizontalWidgets = manager.getWidgetsForLayout(Layout.HORIZONTAL).first()
        val verticalWidgets = manager.getWidgetsForLayout(Layout.VERTICAL).first()

        assertEquals(1, horizontalWidgets.size)
        assertEquals(1, verticalWidgets.size)
        assertEquals(Layout.HORIZONTAL, horizontalWidgets[0].layout)
        assertEquals(Layout.VERTICAL, verticalWidgets[0].layout)
    }

    @Test
    fun `getAllWidgets returns all widgets regardless of layout`() = runTest {
        val manager = WidgetLayoutManager()
        val horizontalWidget = Widget(
            "1",
            "https://example.com/h",
            WidgetPosition(0f, 0f),
            WidgetSize(400, 300),
            Layout.HORIZONTAL
        )
        val verticalWidget = Widget(
            "2",
            "https://example.com/v",
            WidgetPosition(0f, 0f),
            WidgetSize(300, 400),
            Layout.VERTICAL
        )

        manager.addWidget(horizontalWidget)
        manager.addWidget(verticalWidget)

        val allWidgets = manager.getAllWidgets().first()

        assertEquals(2, allWidgets.size)
    }

    @Test
    fun `removeWidget removes widget by id`() = runTest {
        val manager = WidgetLayoutManager()
        val widget = Widget(
            "1",
            "https://example.com/h",
            WidgetPosition(0f, 0f),
            WidgetSize(400, 300),
            Layout.HORIZONTAL
        )

        manager.addWidget(widget)
        assertEquals(1, manager.getAllWidgets().first().size)

        manager.removeWidget("1")
        assertEquals(0, manager.getAllWidgets().first().size)
    }

    @Test
    fun `multiple widgets same layout are all returned`() = runTest {
        val manager = WidgetLayoutManager()
        val widget1 = Widget(
            "1",
            "https://example.com/1",
            WidgetPosition(0f, 0f),
            WidgetSize(400, 300),
            Layout.HORIZONTAL
        )
        val widget2 = Widget(
            "2",
            "https://example.com/2",
            WidgetPosition(100f, 100f),
            WidgetSize(400, 300),
            Layout.HORIZONTAL
        )

        manager.addWidget(widget1)
        manager.addWidget(widget2)

        val horizontalWidgets = manager.getWidgetsForLayout(Layout.HORIZONTAL).first()

        assertEquals(2, horizontalWidgets.size)
    }
}
