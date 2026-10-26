package com.multistream.core.encoding

import android.graphics.Bitmap
import android.view.Surface
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import com.multistream.core.widgets.WidgetRenderer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiLayoutCompositorTest {

    private lateinit var widgetRenderer: WidgetRenderer
    private lateinit var backend: FakeCompositionBackend
    private lateinit var compositor: MultiLayoutCompositor

    private val horizontalWidget = Widget(
        id = "h1",
        url = "https://example.com/horizontal",
        position = WidgetPosition(0.05f, 0.05f),
        size = WidgetSize(400, 300),
        layout = Layout.HORIZONTAL
    )

    private val verticalWidget = Widget(
        id = "v1",
        url = "https://example.com/vertical",
        position = WidgetPosition(0.1f, 0.2f),
        size = WidgetSize(300, 400),
        layout = Layout.VERTICAL
    )

    @Before
    fun setUp() {
        widgetRenderer = mockk()
        coEvery { widgetRenderer.renderWidget(any(), any(), any()) } returns
            mockk<Bitmap>(relaxed = true)
        backend = FakeCompositionBackend()
        compositor = MultiLayoutCompositor(widgetRenderer, backend)
    }

    @After
    fun tearDown() {
        runBlocking { compositor.release() }
        unmockkAll()
    }

    @Test
    fun `compose produces both horizontal and vertical surfaces`() = runTest {
        val result = compositor.compose(
            sources = listOf(mockk<Surface>(relaxed = true)),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = listOf(verticalWidget)
        )

        assertNotNull(result.horizontalSurface)
        assertNotNull(result.verticalSurface)
        assertSame(backend.horizontalSurface, result.horizontalSurface)
        assertSame(backend.verticalSurface, result.verticalSurface)
    }

    @Test
    fun `compose uses the supplied targets when the encoders own the surfaces`() = runTest {
        val horizontalTarget = mockk<Surface>(relaxed = true)
        val verticalTarget = mockk<Surface>(relaxed = true)

        val result = compositor.compose(
            sources = emptyList(),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = listOf(verticalWidget),
            horizontalTarget = horizontalTarget,
            verticalTarget = verticalTarget
        )

        assertSame(horizontalTarget, result.horizontalSurface)
        assertSame(verticalTarget, result.verticalSurface)
        assertSame(horizontalTarget, compositor.getSurface(Layout.HORIZONTAL))
        assertSame(verticalTarget, compositor.getSurface(Layout.VERTICAL))
    }

    @Test
    fun `compose renders every widget through the widget renderer`() = runTest {
        compositor.compose(
            sources = emptyList(),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = listOf(verticalWidget)
        )

        coVerify {
            widgetRenderer.renderWidget(
                horizontalWidget.url,
                horizontalWidget.size.width,
                horizontalWidget.size.height
            )
        }
        coVerify {
            widgetRenderer.renderWidget(
                verticalWidget.url,
                verticalWidget.size.width,
                verticalWidget.size.height
            )
        }
    }

    @Test
    fun `compose draws both layouts exactly once`() = runTest {
        compositor.compose(
            sources = emptyList(),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = listOf(verticalWidget)
        )

        assertEquals(2, backend.drawnFrames.size)
        assertEquals(
            setOf(Layout.HORIZONTAL, Layout.VERTICAL),
            backend.drawnFrames.map { it.first }.toSet()
        )
        assertTrue(
            backend.drawnFrames
                .first { it.first == Layout.HORIZONTAL }
                .second
                .any { it.widgetId == horizontalWidget.id }
        )
        assertTrue(
            backend.drawnFrames
                .first { it.first == Layout.VERTICAL }
                .second
                .any { it.widgetId == verticalWidget.id }
        )
    }

    @Test
    fun `widget count follows the widget lists of each layout`() = runTest {
        compositor.updateWidgets(Layout.HORIZONTAL, listOf(horizontalWidget))
        compositor.updateWidgets(Layout.VERTICAL, listOf(verticalWidget, verticalWidget.copy(id = "v2")))

        assertEquals(1, compositor.getWidgetCount(Layout.HORIZONTAL))
        assertEquals(2, compositor.getWidgetCount(Layout.VERTICAL))
    }

    @Test
    fun `renderFrame returns false before the first composition`() = runTest {
        assertFalse(compositor.renderFrame())
    }

    @Test
    fun `widget bitmaps are cached between frames`() = runTest {
        compositor.compose(
            sources = emptyList(),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = emptyList()
        )
        compositor.compose(
            sources = emptyList(),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = emptyList()
        )

        coVerify(exactly = 1) {
            widgetRenderer.renderWidget(
                horizontalWidget.url,
                horizontalWidget.size.width,
                horizontalWidget.size.height
            )
        }
    }

    private class FakeCompositionBackend : CompositionBackend {
        val horizontalSurface: Surface = mockk(relaxed = true)
        val verticalSurface: Surface = mockk(relaxed = true)
        val drawnFrames = mutableListOf<Pair<Layout, List<WidgetLayer>>>()

        override suspend fun acquireSurface(layout: Layout, requested: Surface?): Surface =
            requested ?: when (layout) {
                Layout.HORIZONTAL -> horizontalSurface
                Layout.VERTICAL -> verticalSurface
            }

        override suspend fun draw(
            layout: Layout,
            layers: List<WidgetLayer>,
            sources: List<Surface>
        ) {
            drawnFrames += layout to layers
        }

        override suspend fun createSourceSurface(width: Int, height: Int): Surface =
            horizontalSurface

        override suspend fun release() = Unit
    }
}
