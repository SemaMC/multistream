package com.multistream.core.capture

import android.content.Context
import android.view.Surface
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CameraCaptureTest {

    // LifecycleRegistry (usado por AlwaysResumedLifecycleOwner) exige detectar
    // el hilo principal, que no existe en tests unitarios JVM.
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var mockContext: Context
    private lateinit var mockProvider: ProcessCameraProvider
    private lateinit var mockFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var mockUseCase: UseCase
    private lateinit var cameraCapture: CameraCapture
    private var createPreviewSurface: Surface? = null

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockProvider = mockk(relaxed = true)
        mockFuture = mockk(relaxed = true)

        every { mockFuture.get() } returns mockProvider
        every { mockFuture.isDone } returns true

        mockkStatic(ProcessCameraProvider::class)
        every { ProcessCameraProvider.getInstance(any()) } returns mockFuture

        // Ni el builder real de CameraX ni la inicialización estática de
        // `Preview` corren sobre el android.jar stubeado de los unit tests:
        // la fuente se construye sustituyendo el use case creado.
        mockUseCase = mockk(relaxed = true)
        cameraCapture = object : CameraCapture(mockContext) {
            override fun createPreview(surface: Surface): UseCase {
                createPreviewSurface = surface
                return mockUseCase
            }
        }
    }

    @After
    fun tearDown() {
        cameraCapture.release()
        unmockkAll()
    }

    @Test
    fun `CameraCapture opens front camera and produces surface`() = runBlocking {
        val surface = cameraCapture.openFrontCamera()

        assertNotNull(surface)
    }

    @Test
    fun `openFrontCamera requests provider once`() = runBlocking {
        cameraCapture.openFrontCamera()

        verify(exactly = 1) { ProcessCameraProvider.getInstance(any()) }
    }

    @Test
    fun `openFrontCamera binds front camera to a lifecycle owner`() = runBlocking {
        val surface = cameraCapture.openFrontCamera()

        val selectorSlot = mutableListOf<CameraSelector>()
        verify { mockProvider.bindToLifecycle(any(), capture(selectorSlot), any<UseCase>()) }

        assertNotNull(selectorSlot.single())
        // El use case atado es el creado por createPreview para ese Surface.
        assertSame(surface, createPreviewSurface)
    }

    @Test
    fun `openFrontCamera unbinds previous camera before binding new one`() = runBlocking {
        cameraCapture.openFrontCamera()
        cameraCapture.openFrontCamera()

        // Un bind por apertura, y un unbind previo por cada una.
        verify(exactly = 2) { mockProvider.bindToLifecycle(any(), any<CameraSelector>(), any<UseCase>()) }
        verify(exactly = 2) { mockProvider.unbindAll() }
    }

    @Test
    fun `release unbinds camera provider and clears resources`() = runBlocking {
        cameraCapture.openFrontCamera()
        cameraCapture.openFrontCamera()

        cameraCapture.release()

        // Uno previo a cada bind y uno por el release de este test; el tearDown
        // no añade ninguno (provider ya limpiado).
        verify(atLeast = 3) { mockProvider.unbindAll() }
    }

    @Test
    fun `release without opening does not crash`() {
        cameraCapture.release()
    }
}
