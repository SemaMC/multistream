package com.multistream.core.encoding

import android.view.Surface
import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DualVideoEncoderTest {

    private lateinit var compositor: MultiLayoutCompositor
    private lateinit var factory: FakeEncoderFactory
    private lateinit var encoder: DualVideoEncoder
    private lateinit var horizontalSurface: Surface
    private lateinit var verticalSurface: Surface

    private val horizontalWidget = Widget(
        id = "h1",
        url = "https://example.com/horizontal",
        position = WidgetPosition(0f, 0f),
        size = WidgetSize(400, 300),
        layout = Layout.HORIZONTAL
    )

    private val verticalWidget = Widget(
        id = "v1",
        url = "https://example.com/vertical",
        position = WidgetPosition(0f, 0f),
        size = WidgetSize(300, 400),
        layout = Layout.VERTICAL
    )

    @Before
    fun setUp() {
        horizontalSurface = mockk(relaxed = true)
        verticalSurface = mockk(relaxed = true)
        compositor = mockk(relaxed = true)
        coEvery {
            compositor.compose(any(), any(), any(), any(), any())
        } returns CompositionResult(horizontalSurface, verticalSurface)

        factory = FakeEncoderFactory(listOf(horizontalSurface, verticalSurface))
        encoder = DualVideoEncoder(factory)
    }

    @After
    fun tearDown() {
        encoder.release()
        unmockkAll()
    }

    @Test
    fun `configure configures both encoders`() = runTest {
        encoder.configure(
            compositor = compositor,
            sources = listOf(mockk<Surface>(relaxed = true)),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = listOf(verticalWidget)
        )

        assertNotNull(encoder.getEncoder(Layout.HORIZONTAL))
        assertNotNull(encoder.getEncoder(Layout.VERTICAL))
        assertEquals(2, factory.created.size)
        assertSame(horizontalSurface, factory.created[0].configuredSurface)
        assertSame(verticalSurface, factory.created[1].configuredSurface)
    }

    @Test
    fun `configure hands the encoder surfaces to the compositor`() = runTest {
        encoder.configure(
            compositor = compositor,
            sources = emptyList(),
            horizontalWidgets = listOf(horizontalWidget),
            verticalWidgets = listOf(verticalWidget)
        )

        coVerify {
            compositor.compose(any(), any(), any(), horizontalSurface, verticalSurface)
        }
        assertEquals(horizontalSurface, encoder.composition?.horizontalSurface)
        assertEquals(verticalSurface, encoder.composition?.verticalSurface)
    }

    @Test
    fun `encode produces a separate stream per layout`() = runTest {
        encoder.configure(
            compositor = compositor,
            sources = emptyList(),
            horizontalWidgets = emptyList(),
            verticalWidgets = emptyList()
        )

        val horizontalChunks: List<EncodedChunk> =
            encoder.encode(Layout.HORIZONTAL).take(5).toList()
        val verticalChunks: List<EncodedChunk> =
            encoder.encode(Layout.VERTICAL).take(5).toList()

        assertEquals(5, horizontalChunks.size)
        assertEquals(5, verticalChunks.size)
        assertTrue(horizontalChunks.all { it.layout == Layout.HORIZONTAL })
        assertTrue(verticalChunks.all { it.layout == Layout.VERTICAL })
        assertTrue(horizontalChunks.all { it.isVideo })
        assertTrue(verticalChunks.all { it.isVideo })
    }

    @Test
    fun `encode completes without chunks before configure`() = runTest {
        assertTrue(encoder.encode(Layout.HORIZONTAL).toList().isEmpty())
        assertTrue(encoder.encode(Layout.VERTICAL).toList().isEmpty())
    }

    @Test
    fun `release releases both encoders and forgets the composition`() = runTest {
        encoder.configure(
            compositor = compositor,
            sources = emptyList(),
            horizontalWidgets = emptyList(),
            verticalWidgets = emptyList()
        )
        val horizontal = factory.created[0]
        val vertical = factory.created[1]

        encoder.release()

        assertTrue(horizontal.released)
        assertTrue(vertical.released)
        assertNull(encoder.getEncoder(Layout.HORIZONTAL))
        assertNull(encoder.getEncoder(Layout.VERTICAL))
        assertNull(encoder.composition)
        assertFalse(encoder.renderFrame())
    }

    private class FakeVideoEncoder(private val ownSurface: Surface) : VideoEncoder() {
        var configuredSurface: Surface? = null
            private set
        var released: Boolean = false
            private set

        override fun configure(
            width: Int,
            height: Int,
            fps: Int,
            bitrate: Int,
            inputSurface: Surface?
        ): Surface {
            val target = inputSurface ?: ownSurface
            configuredSurface = target
            return target
        }

        override fun encode(): Flow<EncodedChunkBase> = flow {
            repeat(CHUNKS_PER_STREAM) { index ->
                emit(
                    EncodedChunkBase(
                        data = byteArrayOf(index.toByte()),
                        timestamp = index.toLong(),
                        isKeyFrame = index == 0
                    )
                )
            }
        }

        override fun release() {
            released = true
        }

        private companion object {
            const val CHUNKS_PER_STREAM = 5
        }
    }

    private class FakeEncoderFactory(
        private val surfaces: List<Surface>
    ) : VideoEncoderFactory {
        val created = mutableListOf<FakeVideoEncoder>()

        override fun create(): VideoEncoder {
            val encoder = FakeVideoEncoder(surfaces[created.size])
            created += encoder
            return encoder
        }
    }
}
