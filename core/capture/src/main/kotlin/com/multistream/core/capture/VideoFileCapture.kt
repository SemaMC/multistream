package com.multistream.core.capture

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Fuente de video a partir de un archivo o [Uri] de video, reproducido con
 * ExoPlayer (Media3). Los frames decodificados se dibujan en el [Surface]
 * devuelto hasta que se llama a [release] (reproducción una sola vez; el
 * compositor puede pedir loop vía [ExoPlayer.setRepeatMode]).
 */
class VideoFileCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null
    private var surface: Surface? = null

    /**
     * Carga y reproduce el video en [uri]; devuelve el [Surface] donde se
     * dibujan los frames decodificados.
     */
    fun loadVideo(uri: Uri): Surface {
        player = ExoPlayer.Builder(context).build()
        player?.setMediaItem(MediaItem.fromUri(uri))

        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        this.surface = surface
        player?.setVideoSurface(surface)
        player?.prepare()
        player?.play()

        return surface
    }

    /**
     * Libera el reproductor y su [Surface]. Seguro de llamar aunque nunca se
     * cargara un video.
     */
    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }
}
