package com.multistream.core.widgets

import com.multistream.core.common.Layout
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapHelperTest {

    @Test
    fun `snap alinea al borde izquierdo dentro de tolerancia`() {
        val result = SnapHelper.snap(
            position = WidgetPosition(4f, 0f),
            size = WidgetSize(100, 100),
            layout = Layout.HORIZONTAL,
            enabled = true
        )

        assertEquals(0f, result.position.x, 0.001f)
        assertEquals(0f, result.position.y, 0.001f)
        assertTrue(result.guides.contains(Guideline.LEFT_EDGE))
    }

    @Test
    fun `snap alinea al centro horizontal del panel`() {
        val result = SnapHelper.snap(
            position = WidgetPosition(950f, 0f),
            size = WidgetSize(10, 10),
            layout = Layout.HORIZONTAL,
            enabled = true
        )

        assertEquals(955f, result.position.x, 0.001f)
        assertEquals(0f, result.position.y, 0.001f)
        assertTrue(result.guides.contains(Guideline.H_CENTER))
    }

    @Test
    fun `snap alinea borde derecho y fondo`() {
        val result = SnapHelper.snap(
            position = WidgetPosition(1826f, 976f),
            size = WidgetSize(100, 100),
            layout = Layout.HORIZONTAL,
            enabled = true
        )

        assertEquals(WidgetPosition(1820f, 980f), result.position)
        assertEquals(setOf(Guideline.RIGHT_EDGE, Guideline.BOTTOM_EDGE), result.guides)
    }

    @Test
    fun `fuera de tolerancia no cambia nada`() {
        val result = SnapHelper.snap(
            position = WidgetPosition(50f, 50f),
            size = WidgetSize(100, 100),
            layout = Layout.HORIZONTAL,
            enabled = true
        )

        assertEquals(WidgetPosition(50f, 50f), result.position)
        assertTrue(result.guides.isEmpty())
    }

    @Test
    fun `snap off devuelve la posicion intacta y sin guias`() {
        val result = SnapHelper.snap(
            position = WidgetPosition(4f, 4f),
            size = WidgetSize(100, 100),
            layout = Layout.HORIZONTAL,
            enabled = false
        )

        assertEquals(WidgetPosition(4f, 4f), result.position)
        assertTrue(result.guides.isEmpty())
    }

    @Test
    fun `widget ya centrado no se mueve pero declara las guias`() {
        val result = SnapHelper.snap(
            position = WidgetPosition(532f, 955f),
            size = WidgetSize(16, 10),
            layout = Layout.VERTICAL,
            enabled = true
        )

        assertEquals(WidgetPosition(532f, 955f), result.position)
        assertEquals(setOf(Guideline.H_CENTER, Guideline.V_CENTER), result.guides)
    }
}
