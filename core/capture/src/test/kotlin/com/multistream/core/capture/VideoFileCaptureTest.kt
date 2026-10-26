package com.multistream.core.capture

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class VideoFileCaptureTest {

    private lateinit var mockContext: Context
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var videoFileCapture: VideoFileCapture

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPlayer = mockk(relaxed = true)

        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns mockPlayer

        videoFileCapture = VideoFileCapture(mockContext)
    }

    @After
    fun tearDown() {
        videoFileCapture.release()
        unmockkAll()
    }

    @Test
    fun `loadVideo produces surface`() {
        val surface = videoFileCapture.loadVideo(mockk<Uri>())

        assertNotNull(surface)
    }

    @Test
    fun `loadVideo loads media, attaches surface and starts playback`() {
        videoFileCapture.loadVideo(mockk<Uri>())

        verify(exactly = 1) { mockPlayer.setMediaItem(any<MediaItem>()) }
        verify(exactly = 1) { mockPlayer.setVideoSurface(any<Surface>()) }
        verify(exactly = 1) { mockPlayer.prepare() }
        verify(exactly = 1) { mockPlayer.play() }
    }

    @Test
    fun `release releases player and clears it`() {
        videoFileCapture.loadVideo(mockk<Uri>())

        videoFileCapture.release()

        verify(exactly = 1) { mockPlayer.release() }
    }

    @Test
    fun `second loadVideo creates a new player`() {
        videoFileCapture.loadVideo(mockk<Uri>())
        videoFileCapture.release()
        videoFileCapture.loadVideo(mockk<Uri>())

        verify(exactly = 2) { anyConstructed<ExoPlayer.Builder>().build() }
    }

    @Test
    fun `release without loading does not crash`() {
        videoFileCapture.release()
    }
}
