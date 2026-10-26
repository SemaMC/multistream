package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelMapperTest {

    private val horizontalPanel = PanelDims(width = 480, height = 270)
    private val verticalPanel = PanelDims(width = 540, height = 960)

    @Test
    fun `layoutSizeFor devuelve 1920x1080 para HORIZONTAL y 1080x1920 para VERTICAL`() {
        assertEquals(WidgetSize(1920, 1080), PanelMapper.layoutSizeFor(Layout.HORIZONTAL))
        assertEquals(WidgetSize(1080, 1920), PanelMapper.layoutSizeFor(Layout.VERTICAL))
    }

    @Test
    fun `toLayoutPosition es proporcional en ambos layouts`() {
        assertEquals(
            WidgetPosition(1920f, 1080f),
            PanelMapper.toLayoutPosition(480f, 270f, horizontalPanel, Layout.HORIZONTAL)
        )
        assertEquals(
            WidgetPosition(540f, 960f),
            PanelMapper.toLayoutPosition(270f, 480f, verticalPanel, Layout.VERTICAL)
        )
    }

    @Test
    fun `toLayoutPosition y toTouchPosition son inversas exactas`() {
        val horizontalTouch = WidgetPosition(123.5f, 87.25f)
        val horizontalLayout = PanelMapper.toLayoutPosition(
            horizontalTouch.x,
            horizontalTouch.y,
            horizontalPanel,
            Layout.HORIZONTAL
        )
        val horizontalBack = PanelMapper.toTouchPosition(
            horizontalLayout,
            horizontalPanel,
            Layout.HORIZONTAL
        )

        assertEquals(horizontalTouch.x, horizontalBack.x, 0.001f)
        assertEquals(horizontalTouch.y, horizontalBack.y, 0.001f)

        val verticalTouch = WidgetPosition(200f, 350f)
        val verticalLayout = PanelMapper.toLayoutPosition(
            verticalTouch.x,
            verticalTouch.y,
            verticalPanel,
            Layout.VERTICAL
        )
        val verticalBack = PanelMapper.toTouchPosition(
            verticalLayout,
            verticalPanel,
            Layout.VERTICAL
        )

        assertEquals(verticalTouch.x, verticalBack.x, 0.001f)
        assertEquals(verticalTouch.y, verticalBack.y, 0.001f)
    }

    @Test
    fun `toLayoutSize redondea al px-layout mas proximo`() {
        assertEquals(
            WidgetSize(480, 24),
            PanelMapper.toLayoutSize(120f, 6f, horizontalPanel, Layout.HORIZONTAL)
        )
    }

    @Test
    fun `clampSize fuerza minimo 100x100`() {
        assertEquals(100, PanelMapper.MIN_SIZE)
        assertEquals(WidgetSize(100, 100), PanelMapper.clampSize(50, 0))
        assertEquals(WidgetSize(300, 400), PanelMapper.clampSize(300, 400))
    }
}
