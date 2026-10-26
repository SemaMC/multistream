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
    private var surfaceTexture: SurfaceTexture? = null

    /**
     * Carga y reproduce el video en [uri]; devuelve el [Surface] donde se
     * dibujan los frames decodificados.
     *
     * Si había un reproductor anterior, lo libera junto a su [Surface] antes
     * de crear los nuevos, para no filtrarlos si se recarga el video sin
     * pasar por [release].
     */
    fun loadVideo(uri: Uri): Surface {
        release()

        val player = ExoPlayer.Builder(context).build()
        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)

        this.player = player
        this.surfaceTexture = surfaceTexture
        this.surface = surface

        player.setMediaItem(MediaItem.fromUri(uri))
        player.setVideoSurface(surface)
        player.prepare()
        player.play()

        return surface
    }

    /**
     * Libera el reproductor, su [Surface] y el [SurfaceTexture] de origen.
     * Seguro de llamar aunque nunca se cargara un video.
     */
    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }
}
