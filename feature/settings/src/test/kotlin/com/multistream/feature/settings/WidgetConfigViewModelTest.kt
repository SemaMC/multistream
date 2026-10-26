package com.multistream.feature.settings

import com.multistream.core.common.Layout
import com.multistream.core.widgets.WidgetLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Local unit tests for [WidgetConfigViewModel].
 *
 * They cover the add/remove contract the screen drives: widgets land in the layout they were
 * created for, are never mixed between the horizontal and vertical compositions, and removal only
 * drops the requested widget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var manager: WidgetLayoutManager
    private lateinit var viewModel: WidgetConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        manager = WidgetLayoutManager()
        viewModel = WidgetConfigViewModel(manager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `both layouts start empty`() {
        assertTrue(viewModel.horizontalWidgets.value.isEmpty())
        assertTrue(viewModel.verticalWidgets.value.isEmpty())
    }

    @Test
    fun `addWidget stores the widget in its own layout only`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/overlay", 10f, 20f, 320, 180)

        val horizontal = viewModel.horizontalWidgets.value
        assertEquals(1, horizontal.size)
        assertEquals("https://example.com/overlay", horizontal.single().url)
        assertEquals(Layout.HORIZONTAL, horizontal.single().layout)
        assertEquals(10f, horizontal.single().position.x)
        assertEquals(20f, horizontal.single().position.y)
        assertEquals(320, horizontal.single().size.width)
        assertEquals(180, horizontal.single().size.height)
        assertTrue(viewModel.verticalWidgets.value.isEmpty())
    }

    @Test
    fun `each layout keeps its own widgets`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/h", 0f, 0f, 100, 100)
        viewModel.addWidget(Layout.VERTICAL, "https://example.com/v", 0f, 0f, 200, 200)

        assertEquals(1, viewModel.horizontalWidgets.value.size)
        assertEquals(1, viewModel.verticalWidgets.value.size)
        assertEquals("https://example.com/v", viewModel.verticalWidgets.value.single().url)
    }

    @Test
    fun `removeWidget drops only the requested widget`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 0f, 0f, 100, 100)
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/b", 0f, 0f, 100, 100)
        val removed = viewModel.horizontalWidgets.value.first()

        viewModel.removeWidget(removed.id)

        val remaining = viewModel.horizontalWidgets.value
        assertEquals(1, remaining.size)
        assertEquals("https://example.com/b", remaining.single().url)
    }

    @Test
    fun `addWidget generates a different id for every widget`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 0f, 0f, 100, 100)
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/b", 0f, 0f, 100, 100)

        val ids = viewModel.horizontalWidgets.value.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `moveWidget aplica snap y actualiza posicion`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 4f, 0f, 100, 100)
        val id = viewModel.horizontalWidgets.value.single().id

        viewModel.moveWidget(id, 4f, 0f, snapEnabled = true)

        val moved = viewModel.horizontalWidgets.value.single()
        assertEquals(0f, moved.position.x)
        assertEquals(0f, moved.position.y)
    }

    @Test
    fun `moveWidget con snap off respeta el valor dado`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 0f, 0f, 100, 100)
        val id = viewModel.horizontalWidgets.value.single().id

        viewModel.moveWidget(id, 4f, 0f, snapEnabled = false)

        val moved = viewModel.horizontalWidgets.value.single()
        assertEquals(4f, moved.position.x)
        assertEquals(0f, moved.position.y)
    }

    @Test
    fun `moveWidget con id inexistente es no-op`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 10f, 20f, 320, 180)

        viewModel.moveWidget("does-not-exist", 0f, 0f, snapEnabled = true)

        val widget = viewModel.horizontalWidgets.value.single()
        assertEquals(10f, widget.position.x)
        assertEquals(20f, widget.position.y)
    }

    @Test
    fun `resizeWidget clampa al minimo`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 0f, 0f, 480, 270)
        val id = viewModel.horizontalWidgets.value.single().id

        viewModel.resizeWidget(id, 50, 0)

        val resized = viewModel.horizontalWidgets.value.single()
        assertEquals(100, resized.size.width)
        assertEquals(100, resized.size.height)
    }

    @Test
    fun `resizeWidget sin tocar posicion`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 10f, 20f, 320, 180)
        val id = viewModel.horizontalWidgets.value.single().id

        viewModel.resizeWidget(id, 640, 360)

        val resized = viewModel.horizontalWidgets.value.single()
        assertEquals(10f, resized.position.x)
        assertEquals(20f, resized.position.y)
        assertEquals(640, resized.size.width)
        assertEquals(360, resized.size.height)
    }

    @Test
    fun `addWidget centrado con 480x270 y url vacia por defecto`() {
        viewModel.addWidget(Layout.HORIZONTAL)

        val added = viewModel.horizontalWidgets.value.single()
        assertEquals(Layout.HORIZONTAL, added.layout)
        assertEquals(720f, added.position.x)
        assertEquals(405f, added.position.y)
        assertEquals(480, added.size.width)
        assertEquals(270, added.size.height)
        assertEquals("", added.url)
    }

    @Test
    fun `setWidgetUrl actualiza la url sin tocar position ni size`() {
        viewModel.addWidget(Layout.HORIZONTAL, "https://example.com/a", 10f, 20f, 320, 180)
        val id = viewModel.horizontalWidgets.value.single().id

        viewModel.setWidgetUrl(id, "https://example.com/b")

        val updated = viewModel.horizontalWidgets.value.single()
        assertEquals("https://example.com/b", updated.url)
        assertEquals(10f, updated.position.x)
        assertEquals(20f, updated.position.y)
        assertEquals(320, updated.size.width)
        assertEquals(180, updated.size.height)
    }
}
