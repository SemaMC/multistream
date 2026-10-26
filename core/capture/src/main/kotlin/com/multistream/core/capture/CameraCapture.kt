package com.multistream.core.capture

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de video en vivo: la cámara frontal, vía CameraX.
 *
 * Produce un [Surface] sobre el que la vista previa de la cámara dibuja sus
 * frames; el compositor lo consume como cualquier otra fuente de video.
 *
 * Nota sobre el ciclo de vida: CameraX exige atar el use case a un
 * [LifecycleOwner] en estado RESUMED. La fuente es un servicio sin UI, por lo
 * que se usa un owner propio siempre resumido; el desbloqueo del recurso es
 * [release] (unbind), no el estado del owner.
 */
@Singleton
open class CameraCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private val lifecycleOwner = AlwaysResumedLifecycleOwner()

    // Executor en el que la vista previa entrega su SurfaceRequest.
    private val surfaceExecutor = Executors.newSingleThreadExecutor()

    /**
     * Abre la cámara frontal y devuelve el [Surface] donde dibuja.
     *
     * Si había una cámara atada anteriormente, la desata antes de atar la
     * nueva (CameraX solo permite un use case de preview por provider).
     */
    suspend fun openFrontCamera(): Surface {
        cameraProvider = ProcessCameraProvider.getInstance(context).await()

        val surfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        }
        val previewSurface = Surface(surfaceTexture)
        this.surfaceTexture = surfaceTexture
        this.surface = previewSurface

        val preview = createPreview(previewSurface)

        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview
        )

        return previewSurface
    }

    /**
     * Crea el use case de vista previa y le entrega el [surface] en el que
     * CameraX dibujará los frames de la cámara. `open` para que los tests
     * unitarios sustituyan el use case por un mock: ni el builder real de
     * CameraX ni la inicialización estática de `Preview` corren sobre el
     * android.jar de stub de los unit tests.
     */
    protected open fun createPreview(surface: Surface): UseCase {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider { request ->
            request.provideSurface(surface, surfaceExecutor) { }
        }
        return preview
    }

    /**
     * Desata la cámara del ciclo de vida y suelta el [Surface] entregado.
     * Seguro de llamar aunque nunca se abriera la cámara.
     */
    fun release() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    /**
     * [LifecycleOwner] que reporta el ciclo de vida completo en RESUMED desde
     * su creación, sin depender de una Activity o Fragment.
     */
    private class AlwaysResumedLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        init {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    private companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
    }
}
