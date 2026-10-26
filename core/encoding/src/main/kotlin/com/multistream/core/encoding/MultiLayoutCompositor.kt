package com.multistream.core.encoding

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import com.multistream.core.common.HORIZONTAL_CONFIG
import com.multistream.core.common.Layout
import com.multistream.core.common.LayoutConfig
import com.multistream.core.common.VERTICAL_CONFIG
import com.multistream.core.common.Widget
import com.multistream.core.widgets.WidgetRenderer
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One widget ready to be drawn by a [CompositionBackend].
 *
 * [left], [top], [width] and [height] are fractions of the layout size (0..1), so the same widget
 * can be reused by both layouts with different resolutions.
 */
data class WidgetLayer(
    val widgetId: String,
    val bitmap: Bitmap,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/**
 * Low level rendering backend used by [MultiLayoutCompositor].
 *
 * Splitting the backend from the orchestration keeps the compositor testable without an OpenGL
 * context: production uses [GlCompositionBackend], tests can provide a fake. All methods are
 * suspending because the GL implementation hops to its own single threaded dispatcher (EGL
 * contexts are thread affine).
 */
interface CompositionBackend {
    /**
     * Acquires the render target for [layout].
     *
     * @param requested surface to render into (in production, the encoder input surface). When
     *   `null` the backend creates an offscreen surface it owns.
     */
    suspend fun acquireSurface(layout: Layout, requested: Surface?): Surface

    /** Draws one frame for [layout] using the supplied widget layers and video sources. */
    suspend fun draw(layout: Layout, layers: List<WidgetLayer>, sources: List<Surface>)

    /**
     * Creates a surface that feeds the composition as a video source. The caller renders into the
     * returned surface (for example with a VirtualDisplay) and passes it back in [draw].
     */
    suspend fun createSourceSurface(width: Int, height: Int): Surface

    /** Releases every GL/EGL resource held by the backend. */
    suspend fun release()
}

/**
 * Composes the horizontal (1920x1080) and vertical (1080x1920) layouts simultaneously.
 *
 * The compositor keeps the widget configuration of each layout, renders every widget through
 * [WidgetRenderer] once (bitmaps are cached by URL and size) and asks the backend to draw both
 * layouts in the same call. Widgets are composited directly into the composition surfaces; no
 * overlay window is involved, as required by the design.
 *
 * Production usage is driven by [DualVideoEncoder.configure], which hands the encoder input
 * surfaces to [compose]. Re-render frames with [renderFrame] at the capture frame rate.
 */
@Singleton
class MultiLayoutCompositor @Inject constructor(
    private val widgetRenderer: WidgetRenderer,
    private val backend: CompositionBackend
) {
    /** Convenience constructor that wires the production OpenGL backend. */
    constructor(widgetRenderer: WidgetRenderer) : this(widgetRenderer, GlCompositionBackend())

    private val mutex = Mutex()
    private val horizontalWidgets = Collections.synchronizedList(mutableListOf<Widget>())
    private val verticalWidgets = Collections.synchronizedList(mutableListOf<Widget>())
    private val widgetBitmaps = ConcurrentHashMap<String, Bitmap>()

    @Volatile
    private var horizontalSurface: Surface? = null

    @Volatile
    private var verticalSurface: Surface? = null

    private var horizontalLayers: List<WidgetLayer> = emptyList()
    private var verticalLayers: List<WidgetLayer> = emptyList()
    private var sources: List<Surface> = emptyList()

    /**
     * Renders both layouts and returns their composition surfaces.
     *
     * The backend creates offscreen surfaces when called through this overload. The encoder
     * pipeline should use the overload that receives the codec input surfaces.
     */
    suspend fun compose(
        sources: List<Surface>,
        horizontalWidgets: List<Widget>,
        verticalWidgets: List<Widget>
    ): CompositionResult = compose(sources, horizontalWidgets, verticalWidgets, null, null)

    /**
     * Renders both layouts into the supplied surfaces (or into freshly created offscreen surfaces
     * when a target is `null`).
     *
     * Widget bitmaps are rendered through [WidgetRenderer] before any GL work, so this method may
     * suspend for as long as the WebViews take to load.
     */
    suspend fun compose(
        sources: List<Surface>,
        horizontalWidgets: List<Widget>,
        verticalWidgets: List<Widget>,
        horizontalTarget: Surface?,
        verticalTarget: Surface?
    ): CompositionResult = mutex.withLock {
        replaceWidgets(this.horizontalWidgets, horizontalWidgets)
        replaceWidgets(this.verticalWidgets, verticalWidgets)

        val newHorizontalLayers = buildLayers(horizontalWidgets, HORIZONTAL_CONFIG)
        val newVerticalLayers = buildLayers(verticalWidgets, VERTICAL_CONFIG)
        val sourceList = sources.toList()

        val horizontal = backend.acquireSurface(Layout.HORIZONTAL, horizontalTarget)
        val vertical = backend.acquireSurface(Layout.VERTICAL, verticalTarget)

        horizontalLayers = newHorizontalLayers
        verticalLayers = newVerticalLayers
        this.sources = sourceList
        horizontalSurface = horizontal
        verticalSurface = vertical

        backend.draw(Layout.HORIZONTAL, horizontalLayers, sourceList)
        backend.draw(Layout.VERTICAL, verticalLayers, sourceList)

        CompositionResult(horizontal, vertical)
    }

    /**
     * Replaces the widgets of a single layout and redraws immediately when the compositor is
     * already composed. Used when the user adds or removes a widget mid-stream.
     */
    suspend fun updateWidgets(layout: Layout, widgets: List<Widget>) = mutex.withLock {
        val layers = buildLayers(widgets, layout.config())
        when (layout) {
            Layout.HORIZONTAL -> {
                replaceWidgets(horizontalWidgets, widgets)
                horizontalLayers = layers
            }

            Layout.VERTICAL -> {
                replaceWidgets(verticalWidgets, widgets)
                verticalLayers = layers
            }
        }
        if (getSurface(layout) != null) {
            backend.draw(layout, layers, sources)
        }
    }

    /**
     * Draws one more frame for both layouts using the current widget layers and video sources.
     *
     * [compose] already produces the first frame; call this from the capture loop (or a timer at
     * the layout frame rate) to keep feeding the encoders.
     *
     * @return `false` when [compose] has not been called yet.
     */
    suspend fun renderFrame(): Boolean = mutex.withLock {
        if (horizontalSurface == null || verticalSurface == null) {
            return@withLock false
        }
        backend.draw(Layout.HORIZONTAL, horizontalLayers, sources)
        backend.draw(Layout.VERTICAL, verticalLayers, sources)
        true
    }

    /**
     * Creates a surface the capture layer can render into to feed the composition as video.
     *
     * Surfaces that were not created here cannot be sampled by OpenGL and are ignored by the
     * backend (see [GlCompositionBackend]).
     */
    suspend fun createSourceSurface(width: Int, height: Int): Surface =
        backend.createSourceSurface(width, height)

    /** Number of widgets currently configured for [layout]. */
    fun getWidgetCount(layout: Layout): Int = when (layout) {
        Layout.HORIZONTAL -> horizontalWidgets.size
        Layout.VERTICAL -> verticalWidgets.size
    }

    /** Composition surface currently used by [layout], or `null` before the first [compose]. */
    fun getSurface(layout: Layout): Surface? = when (layout) {
        Layout.HORIZONTAL -> horizontalSurface
        Layout.VERTICAL -> verticalSurface
    }

    /** Releases the backend and forgets the composition state. Safe to call repeatedly. */
    suspend fun release() = mutex.withLock {
        backend.release()
        horizontalSurface = null
        verticalSurface = null
        horizontalLayers = emptyList()
        verticalLayers = emptyList()
        sources = emptyList()
        horizontalWidgets.clear()
        verticalWidgets.clear()
        widgetBitmaps.clear()
    }

    private suspend fun buildLayers(
        widgets: List<Widget>,
        config: LayoutConfig
    ): List<WidgetLayer> = coroutineScope {
        widgets.map { widget ->
            async { renderLayer(widget, config) }
        }.awaitAll()
    }

    private suspend fun renderLayer(widget: Widget, config: LayoutConfig): WidgetLayer {
        val cacheKey = "${widget.url}|${widget.size.width}x${widget.size.height}"
        val bitmap = widgetBitmaps[cacheKey]
            ?: widgetRenderer
                .renderWidget(widget.url, widget.size.width, widget.size.height)
                .also { widgetBitmaps[cacheKey] = it }

        val width = (widget.size.width.toFloat() / config.width).coerceIn(0f, 1f)
        val height = (widget.size.height.toFloat() / config.height).coerceIn(0f, 1f)
        val left = fractionOf(widget.position.x, config.width)
            .coerceIn(0f, (1f - width).coerceAtLeast(0f))
        val top = fractionOf(widget.position.y, config.height)
            .coerceIn(0f, (1f - height).coerceAtLeast(0f))

        return WidgetLayer(
            widgetId = widget.id,
            bitmap = bitmap,
            left = left,
            top = top,
            width = width,
            height = height
        )
    }

    /**
     * Widget positions are fractions of the layout when they are inside 0..1 and pixels
     * otherwise, so both coordinate conventions used by the editor keep working.
     */
    private fun fractionOf(value: Float, dimension: Int): Float {
        val fraction = if (value in 0f..1f) value else value / dimension
        return fraction.coerceIn(0f, 1f)
    }

    private fun replaceWidgets(target: MutableList<Widget>, widgets: List<Widget>) {
        synchronized(target) {
            target.clear()
            target.addAll(widgets)
        }
    }

    private fun Layout.config(): LayoutConfig = when (this) {
        Layout.HORIZONTAL -> HORIZONTAL_CONFIG
        Layout.VERTICAL -> VERTICAL_CONFIG
    }
}

/**
 * OpenGL ES implementation of [CompositionBackend].
 *
 * One EGL context is shared by both layouts. Every call hops to a dedicated single thread because
 * an EGL context can only be current on one thread at a time. Widgets are uploaded as RGBA
 * textures and drawn as alpha blended quads; video sources are sampled as external OES textures.
 *
 * A source passed to [draw] must have been created by [createSourceSurface]; arbitrary surfaces
 * cannot be sampled by OpenGL and are logged and skipped.
 */
class GlCompositionBackend : CompositionBackend {

    private val glDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "MultiLayoutCompositor-GL").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private var engine: GlCompositionEngine? = null

    override suspend fun acquireSurface(layout: Layout, requested: Surface?): Surface =
        withContext(glDispatcher) {
            engine().acquireTarget(layout, requested).surface
        }

    override suspend fun draw(
        layout: Layout,
        layers: List<WidgetLayer>,
        sources: List<Surface>
    ) {
        withContext(glDispatcher) {
            engine().draw(layout, layers, sources)
        }
    }

    override suspend fun createSourceSurface(width: Int, height: Int): Surface =
        withContext(glDispatcher) {
            engine().createSourceSurface(width, height)
        }

    override suspend fun release() {
        withContext(glDispatcher) {
            engine?.release()
            engine = null
        }
    }

    private fun engine(): GlCompositionEngine = engine ?: GlCompositionEngine().also {
        it.initialize()
        engine = it
    }
}

private class GlCompositionEngine {
    private val egl = EglEnvironment()
    private val targets = HashMap<Layout, CompositionTarget>()
    private val sourceTextures = IdentityHashMap<Surface, SourceTexture>()
    private val widgetTextures = IdentityHashMap<Bitmap, Int>()
    private val warnedSources: MutableSet<Surface> =
        Collections.newSetFromMap(IdentityHashMap<Surface, Boolean>())
    private var widgetProgram: TexturedQuadProgram? = null
    private var externalProgram: TexturedQuadProgram? = null

    fun initialize() {
        egl.initialize()
        widgetProgram = TexturedQuadProgram(VERTEX_SHADER, FRAGMENT_TEXTURE_2D)
        // SurfaceTexture buffers are top-down, so the sampled texture is flipped vertically
        // relative to a plain Bitmap upload.
        externalProgram = TexturedQuadProgram(
            VERTEX_SHADER,
            FRAGMENT_EXTERNAL_OES,
            topTexCoordIsOne = true
        )
    }

    fun acquireTarget(layout: Layout, requested: Surface?): CompositionTarget {
        val existing = targets[layout]
        if (existing != null && (requested == null || existing.surface === requested)) {
            return existing
        }
        existing?.let { destroyTarget(it) }

        val config = layout.config()
        val target = if (requested != null) {
            CompositionTarget(
                layout = layout,
                surface = requested,
                config = config,
                eglSurface = egl.createWindowSurface(requested),
                offscreenSurfaceTexture = null
            )
        } else {
            val textureId = createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            val surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture.setDefaultBufferSize(config.width, config.height)
            val surface = Surface(surfaceTexture)
            CompositionTarget(
                layout = layout,
                surface = surface,
                config = config,
                eglSurface = egl.createWindowSurface(surface),
                offscreenSurfaceTexture = surfaceTexture
            )
        }
        targets[layout] = target
        return target
    }

    fun createSourceSurface(width: Int, height: Int): Surface {
        require(width > 0 && height > 0) { "Invalid source size: ${width}x$height" }
        val textureId = createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        val surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(width, height)
        val surface = Surface(surfaceTexture)
        sourceTextures[surface] = SourceTexture(
            surface = surface,
            surfaceTexture = surfaceTexture,
            textureId = textureId,
            width = width,
            height = height
        )
        return surface
    }

    fun draw(layout: Layout, layers: List<WidgetLayer>, sources: List<Surface>) {
        val target = targets[layout]
            ?: throw IllegalStateException("No composition target for $layout; call acquireSurface first")

        egl.makeCurrent(target.eglSurface)
        GLES20.glViewport(0, 0, target.config.width, target.config.height)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        drawSources(target, sources)
        drawWidgets(layers)

        GLES20.glDisable(GLES20.GL_BLEND)
        check(EGL14.eglSwapBuffers(egl.display, target.eglSurface)) {
            "eglSwapBuffers failed for $layout"
        }
        // Consume the offscreen frame so its producer never runs out of buffers.
        target.offscreenSurfaceTexture?.updateTexImage()
    }

    fun release() {
        targets.values.forEach { destroyTarget(it) }
        targets.clear()

        sourceTextures.values.forEach { source ->
            runCatching { source.surface.release() }
            runCatching { source.surfaceTexture.release() }
            GLES20.glDeleteTextures(1, intArrayOf(source.textureId), 0)
        }
        sourceTextures.clear()
        warnedSources.clear()

        val textureIds = widgetTextures.values.toIntArray()
        if (textureIds.isNotEmpty()) {
            GLES20.glDeleteTextures(textureIds.size, textureIds, 0)
        }
        widgetTextures.clear()

        widgetProgram?.release()
        widgetProgram = null
        externalProgram?.release()
        externalProgram = null
        egl.release()
    }

    private fun drawSources(target: CompositionTarget, sources: List<Surface>) {
        if (sources.isEmpty()) return
        val program = externalProgram ?: return
        val texMatrix = FloatArray(16)

        sources.forEach { surface ->
            val source = sourceTextures[surface]
            if (source == null) {
                if (warnedSources.add(surface)) {
                    Log.w(
                        TAG,
                        "Source surface is not owned by the compositor and cannot be sampled; " +
                            "create it with createSourceSurface()"
                    )
                }
                return@forEach
            }
            try {
                source.surfaceTexture.updateTexImage()
                source.surfaceTexture.getTransformMatrix(texMatrix)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not update source texture", error)
                return@forEach
            }

            val rect = fitCenterRect(source.width, source.height, target.config)
            program.draw(
                textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId = source.textureId,
                left = rect[0],
                top = rect[1],
                right = rect[2],
                bottom = rect[3],
                texMatrix = texMatrix
            )
        }
    }

    private fun drawWidgets(layers: List<WidgetLayer>) {
        if (layers.isEmpty()) return
        val program = widgetProgram ?: return
        // Widgets are drawn at full opacity; the compositor is the topmost layer of the stream.
        val identityMatrix = IDENTITY_MATRIX

        layers.forEach { layer ->
            val textureId = widgetTexture(layer.bitmap)
            program.draw(
                textureTarget = GLES20.GL_TEXTURE_2D,
                textureId = textureId,
                left = layer.left * 2f - 1f,
                top = 1f - layer.top * 2f,
                right = (layer.left + layer.width) * 2f - 1f,
                bottom = 1f - (layer.top + layer.height) * 2f,
                texMatrix = identityMatrix
            )
        }
    }

    private fun widgetTexture(bitmap: Bitmap): Int {
        widgetTextures[bitmap]?.let { return it }
        val textureId = createTexture(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        widgetTextures[bitmap] = textureId
        return textureId
    }

    private fun createTexture(target: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        check(ids[0] != 0) { "Could not allocate a GL texture" }
        GLES20.glBindTexture(target, ids[0])
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(target, 0)
        return ids[0]
    }

    private fun destroyTarget(target: CompositionTarget) {
        egl.destroySurface(target.eglSurface)
        if (target.offscreenSurfaceTexture != null) {
            runCatching { target.surface.release() }
            runCatching { target.offscreenSurfaceTexture.release() }
        }
    }

    /** Fits the source into the layout preserving its aspect ratio, as NDC bounds. */
    private fun fitCenterRect(
        sourceWidth: Int,
        sourceHeight: Int,
        config: LayoutConfig
    ): FloatArray {
        val layoutAspect = config.width.toFloat() / config.height
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        return if (sourceAspect > layoutAspect) {
            val scale = layoutAspect / sourceAspect
            floatArrayOf(-1f, scale, 1f, -scale)
        } else {
            val scale = sourceAspect / layoutAspect
            floatArrayOf(-scale, 1f, scale, -1f)
        }
    }

    private fun Layout.config(): LayoutConfig = when (this) {
        Layout.HORIZONTAL -> HORIZONTAL_CONFIG
        Layout.VERTICAL -> VERTICAL_CONFIG
    }

    private companion object {
        const val TAG = "GlCompositionBackend"
        val IDENTITY_MATRIX = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    }
}

/** An EGL render target: either an encoder input surface or an offscreen surface we own. */
private class CompositionTarget(
    val layout: Layout,
    val surface: Surface,
    val config: LayoutConfig,
    val eglSurface: EGLSurface,
    val offscreenSurfaceTexture: SurfaceTexture?
)

/** A video source surface created by the compositor and sampled as an external OES texture. */
private class SourceTexture(
    val surface: Surface,
    val surfaceTexture: SurfaceTexture,
    val textureId: Int,
    val width: Int,
    val height: Int
)

/** Minimal EGL display/config/context holder. All calls must happen on the GL thread. */
private class EglEnvironment {
    var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private set

    private var config: EGLConfig? = null
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT

    fun initialize() {
        if (display != EGL14.EGL_NO_DISPLAY) return

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Could not get the default EGL display" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) {
            "Could not initialize EGL"
        }

        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        if (!chooseConfig(configs, configCount, recordable = true) || configCount[0] == 0) {
            if (!chooseConfig(configs, configCount, recordable = false) || configCount[0] == 0) {
                throw IllegalStateException("No EGL config supports GLES2 window rendering")
            }
        }
        config = configs[0] ?: throw IllegalStateException("EGL returned no config")

        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "Could not create the EGL context" }
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        check(display != EGL14.EGL_NO_DISPLAY) { "EGL is not initialized" }
        val attributes = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attributes, 0)
        check(eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
            "Could not create an EGL window surface"
        }
        return eglSurface
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "eglMakeCurrent failed"
        }
    }

    fun destroySurface(eglSurface: EGLSurface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, eglSurface)
        }
    }

    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        if (context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context)
        }
        EGL14.eglTerminate(display)
        context = EGL14.EGL_NO_CONTEXT
        config = null
        display = EGL14.EGL_NO_DISPLAY
    }

    private fun chooseConfig(
        configs: Array<EGLConfig?>,
        configCount: IntArray,
        recordable: Boolean
    ): Boolean {
        val attributes = if (recordable) {
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
        } else {
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
        }
        return EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, configCount, 0)
    }
}

/**
 * Textured quad program. The texcoord matrix is used by external (camera/screen) textures and is
 * the identity for widget bitmaps.
 */
private class TexturedQuadProgram(
    vertexShader: String,
    fragmentShader: String,
    private val topTexCoordIsOne: Boolean = false
) {
    private val programId = GlProgram.create(vertexShader, fragmentShader)
    private val positionLocation = GLES20.glGetAttribLocation(programId, "aPosition")
    private val texCoordLocation = GLES20.glGetAttribLocation(programId, "aTexCoord")
    private val textureLocation = GLES20.glGetUniformLocation(programId, "uTexture")
    private val texMatrixLocation = GLES20.glGetUniformLocation(programId, "uTexMatrix")

    private val vertexData = FloatArray(VERTEX_COUNT * FLOATS_PER_VERTEX)
    private val vertexBuffer = ByteBuffer
        .allocateDirect(vertexData.size * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun draw(
        textureTarget: Int,
        textureId: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        texMatrix: FloatArray
    ) {
        check(positionLocation >= 0 && texCoordLocation >= 0) {
            "Textured quad program is missing its attributes"
        }

        // Triangle strip: top-left, top-right, bottom-left, bottom-right.
        putVertex(0, left, top, 0f, 0f)
        putVertex(1, right, top, 1f, 0f)
        putVertex(2, left, bottom, 0f, 1f)
        putVertex(3, right, bottom, 1f, 1f)

        vertexBuffer.clear()
        vertexBuffer.put(vertexData)
        vertexBuffer.position(0)

        GLES20.glUseProgram(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glUniform1i(textureLocation, 0)
        if (texMatrixLocation >= 0) {
            GLES20.glUniformMatrix4fv(texMatrixLocation, 1, false, texMatrix, 0)
        }

        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(
            positionLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer
        )
        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glVertexAttribPointer(
            texCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(texCoordLocation)
        GLES20.glBindTexture(textureTarget, 0)
    }

    fun release() {
        GLES20.glDeleteProgram(programId)
    }

    private fun putVertex(index: Int, x: Float, y: Float, u: Float, v: Float) {
        val offset = index * FLOATS_PER_VERTEX
        vertexData[offset] = x
        vertexData[offset + 1] = y
        vertexData[offset + 2] = u
        vertexData[offset + 3] = if (topTexCoordIsOne) 1f - v else v
    }

    private companion object {
        const val VERTEX_COUNT = 4
        const val FLOATS_PER_VERTEX = 4
        const val BYTES_PER_FLOAT = 4
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
    }
}

private object GlProgram {
    fun create(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        check(program != 0) { "Could not create a GL program" }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Could not link the GL program: $log")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Could not create a GL shader" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Could not compile the GL shader: $log")
        }
        return shader
    }
}

private const val VERTEX_SHADER = """
    uniform mat4 uTexMatrix;
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
    }
"""

private const val FRAGMENT_TEXTURE_2D = """
    precision mediump float;
    uniform sampler2D uTexture;
    varying vec2 vTexCoord;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
"""

private const val FRAGMENT_EXTERNAL_OES = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    uniform samplerExternalOES uTexture;
    varying vec2 vTexCoord;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
"""
