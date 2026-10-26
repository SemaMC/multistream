package com.multistream.core.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Fuente de video estática: una imagen cargada desde un [Uri] (galería,
 * recursos de la app, etc.). Se decodifica una vez y se dibuja sobre un
 * [Surface] con el tamaño del bitmap, que queda posterior constante.
 */
class ImageCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Decodifica la imagen en [uri] y devuelve un [Surface] que la dibuja al
     * tamaño exacto del bitmap.
     *
     * @throws IllegalArgumentException si el stream no se puede abrir o el
     * bitmap no se puede decodificar.
     */
    fun loadImage(uri: Uri): Surface {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Cannot decode image from $uri")

        val surfaceTexture = SurfaceTexture(0)
        surfaceTexture.setDefaultBufferSize(bitmap.width, bitmap.height)
        val surface = Surface(surfaceTexture)

        // El dibujo en un Surface solo es posible vía lockCanvas: sobre el
        // canvas bloqueado se pinta el bitmap y al desbloquear se publica el
        // frame. Si la superficie no da canvas (p. ej. antes de conectar al
        // consumidor), la imagen no se dibuja: la fuente queda con el frame
        // pendiente de pintar por el propio consumidor.
        val canvas = surface.lockCanvas(null) ?: return surface
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        surface.unlockCanvasAndPost(canvas)
        return surface
    }
}
