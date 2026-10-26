package com.multistream.core.capture

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.InputStream

class ImageCaptureTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockInputStream: InputStream
    private lateinit var imageCapture: ImageCapture

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockInputStream = mockk(relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContentResolver.openInputStream(any()) } returns mockInputStream

        mockkStatic(BitmapFactory::class)

        imageCapture = ImageCapture(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loadImage produces surface for decoded bitmap`() {
        val uri = mockk<Uri>()
        every { BitmapFactory.decodeStream(ofType<InputStream>()) } returns mockBitmap(1920, 1080)

        val surface = imageCapture.loadImage(uri)

        assertNotNull(surface)
        verify(exactly = 1) { BitmapFactory.decodeStream(ofType<InputStream>()) }
    }

    @Test
    fun `loadImage throws when bitmap cannot be decoded`() {
        val uri = mockk<Uri>()
        every { BitmapFactory.decodeStream(ofType<InputStream>()) } returns null

        try {
            imageCapture.loadImage(uri)
            fail("Expected IllegalArgumentException for undecodable image")
        } catch (expected: IllegalArgumentException) {
            // Camino esperado
        }
    }

    private fun mockBitmap(width: Int, height: Int): Bitmap = mockk {
        every { this@mockk.width } returns width
        every { this@mockk.height } returns height
    }
}
