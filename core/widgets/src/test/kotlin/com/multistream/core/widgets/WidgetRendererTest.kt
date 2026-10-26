package com.multistream.core.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetRendererTest {

    @Before
    fun setup() {
        mockkStatic(Bitmap::class)
        mockkConstructor(WebView::class)
        mockkConstructor(Canvas::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `WidgetRenderer renders WebView to bitmap with async loading`() = runTest {
        // Setup mocks
        val mockContext = mockk<Context>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 400
        every { mockBitmap.height } returns 300
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) } returns mockBitmap

        // Mock WebView
        val mockWebSettings = mockk<WebSettings>(relaxed = true)
        every { anyConstructed<WebView>().settings } returns mockWebSettings
        
        // Capture WebViewClient to trigger onPageFinished
        val clientSlot = slot<WebViewClient>()
        val mockWebViewForCallback = mockk<WebView>(relaxed = true)
        every { anyConstructed<WebView>().webViewClient = capture(clientSlot) } just Runs
        every { anyConstructed<WebView>().loadUrl(any<String>()) } answers {
            // Simulate page loaded immediately
            clientSlot.captured.onPageFinished(mockWebViewForCallback, "test")
        }
        every { anyConstructed<WebView>().layout(any(), any(), any(), any()) } just Runs
        every { anyConstructed<WebView>().draw(any<Canvas>()) } just Runs

        val renderer = WidgetRenderer(mockContext)
        val bitmap = renderer.renderWidget("https://example.com/widget")

        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        
        // Verify WebView was created and configured
        verify { anyConstructed<WebView>().settings }
        verify { anyConstructed<WebView>().loadUrl("https://example.com/widget") }
    }

    @Test
    fun `renderWidget uses default dimensions when not specified`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 400
        every { mockBitmap.height } returns 300
        
        every { Bitmap.createBitmap(400, 300, any()) } returns mockBitmap

        val mockWebSettings = mockk<WebSettings>(relaxed = true)
        every { anyConstructed<WebView>().settings } returns mockWebSettings
        
        val clientSlot = slot<WebViewClient>()
        every { anyConstructed<WebView>().webViewClient = capture(clientSlot) } just Runs
        every { anyConstructed<WebView>().loadUrl(any<String>()) } answers {
            clientSlot.captured.onPageFinished(any(), "test")
        }
        every { anyConstructed<WebView>().layout(any(), any(), any(), any()) } just Runs
        every { anyConstructed<WebView>().draw(any<Canvas>()) } just Runs

        val renderer = WidgetRenderer(mockContext)
        val bitmap = renderer.renderWidget("https://example.com/widget")

        assertNotNull(bitmap)
        verify { Bitmap.createBitmap(400, 300, any()) }
    }

    @Test
    fun `renderWidget uses custom dimensions when specified`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 800
        every { mockBitmap.height } returns 600
        
        every { Bitmap.createBitmap(800, 600, any()) } returns mockBitmap

        val mockWebSettings = mockk<WebSettings>(relaxed = true)
        every { anyConstructed<WebView>().settings } returns mockWebSettings
        
        val clientSlot = slot<WebViewClient>()
        every { anyConstructed<WebView>().webViewClient = capture(clientSlot) } just Runs
        every { anyConstructed<WebView>().loadUrl(any<String>()) } answers {
            clientSlot.captured.onPageFinished(any(), "test")
        }
        every { anyConstructed<WebView>().layout(any(), any(), any(), any()) } just Runs
        every { anyConstructed<WebView>().draw(any<Canvas>()) } just Runs

        val renderer = WidgetRenderer(mockContext)
        val bitmap = renderer.renderWidget("https://example.com/widget", 800, 600)

        assertNotNull(bitmap)
        verify { Bitmap.createBitmap(800, 600, any()) }
    }
}
