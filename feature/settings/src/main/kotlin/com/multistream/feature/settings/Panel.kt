package com.multistream.feature.settings

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget
import com.multistream.core.common.WidgetPosition
import com.multistream.core.common.WidgetSize
import com.multistream.core.widgets.Guideline
import com.multistream.core.widgets.PanelDims
import com.multistream.core.widgets.PanelMapper
import com.multistream.core.widgets.SnapHelper
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val HandleSizeDp = 24f

private val PanelBg = Color(0xFF2C2C2C)
private val PanelBorder = Color(0xFF4A4A4A)
private val WidgetBg = Color(0xFF3A3A3A)
private val WidgetBorder = Color(0xFF8E8E93)
private val SelectedBorder = Color(0xFF3B82F6) // azul
private val PartialBorder = Color(0xFFF59E0B) // amarillo
private val TotalBorder = Color(0xFFEF4444) // rojo
private val TextColor = Color(0xFFEEEEEE)
private val MutedTextColor = Color(0xFF9CA3AF)
private val HandleColor = Color(0xFF3B82F6)
private val HandleBorder = Color(0xFF1565C0)
private val GuideColor = Color(0xFFEF4444)

/** Cómo sobrelaza la caja respecto al área del layout (px de layout). */
enum class OutOfPanel { Inside, Partial, Total }

/** Prioridad de la representación visual de la caja. */
enum class BoxViewState { Normal, Selected, PartialOut, TotalOut, MissingUrl }

/** Evalúa solapamiento de un rect (x,y,w,h) contra el panel (panelW,panelH) en el mismo espacio de coordenadas. */
private fun outOfPanel(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    panelW: Float,
    panelH: Float,
): OutOfPanel {
    val right = x + w
    val bottom = y + h
    val overlaps = x < panelW && right > 0 && y < panelH && bottom > 0
    return when {
        !overlaps -> OutOfPanel.Total
        x >= 0f && y >= 0f && right <= panelW && bottom <= panelH -> OutOfPanel.Inside
        else -> OutOfPanel.Partial
    }
}

private fun boxViewState(widget: Widget, outOfPanel: OutOfPanel, selected: Boolean): BoxViewState =
    when {
        widget.url.isBlank() -> BoxViewState.MissingUrl
        outOfPanel == OutOfPanel.Total -> BoxViewState.TotalOut
        outOfPanel == OutOfPanel.Partial -> BoxViewState.PartialOut
        selected -> BoxViewState.Selected
        else -> BoxViewState.Normal
    }

private fun borderWidth(state: BoxViewState): Dp = when (state) {
    BoxViewState.Normal -> 1.dp
    BoxViewState.Selected -> 2.dp
    BoxViewState.TotalOut -> 2.dp
    BoxViewState.MissingUrl -> 2.dp
    BoxViewState.PartialOut -> 0.dp // discontinuo, se dibuja con Canvas
}

private fun borderColor(state: BoxViewState): Color = when (state) {
    BoxViewState.Normal -> WidgetBorder
    BoxViewState.Selected -> SelectedBorder
    BoxViewState.TotalOut -> TotalBorder
    BoxViewState.MissingUrl -> TotalBorder
    BoxViewState.PartialOut -> PartialBorder
}

/**
 * Guías de snap durante el arrastre. Se predicen por frame desde la posición táctil actual con
 * [SnapHelper] (sin tocar el ViewModel) y se dibujan como líneas discontinuas rojas; desaparecen
 * cuando no hay guías activas.
 */
@Composable
private fun GuidesOverlay(
    guides: Set<Guideline>,
    widgetSize: WidgetSize,
    layout: Layout,
) {
    if (guides.isEmpty()) return

    val layoutSize = PanelMapper.layoutSizeFor(layout)
    val density = LocalDensity.current.density
    val stroke = 2f * density

    Canvas(modifier = Modifier.fillMaxSize()) {
        val panelW = size.width
        val panelH = size.height

        fun layoutXToTouch(x: Float) = x / layoutSize.width * panelW
        fun layoutYToTouch(y: Float) = y / layoutSize.height * panelH
        fun clamp(value: Float, max: Float) = value.coerceIn(0f, max)

        fun drawVertical(layoutX: Float) {
            val x = clamp(layoutXToTouch(layoutX), panelW)
            drawDashedLine(Offset(x, 0f), Offset(x, panelH), GuideColor, stroke)
        }
        fun drawHorizontal(layoutY: Float) {
            val y = clamp(layoutYToTouch(layoutY), panelH)
            drawDashedLine(Offset(0f, y), Offset(panelW, y), GuideColor, stroke)
        }

        val widgetW = widgetSize.width
        val widgetH = widgetSize.height

        guides.forEach { guide ->
            when (guide) {
                Guideline.LEFT_EDGE -> drawVertical(0f)
                Guideline.RIGHT_EDGE -> drawVertical((layoutSize.width - widgetW).toFloat())
                Guideline.H_CENTER -> drawVertical(layoutSize.width / 2f - widgetW / 2f)
                Guideline.TOP_EDGE -> drawHorizontal(0f)
                Guideline.BOTTOM_EDGE -> drawHorizontal((layoutSize.height - widgetH).toFloat())
                Guideline.V_CENTER -> drawHorizontal(layoutSize.height / 2f - widgetH / 2f)
            }
        }
    }
}

private fun DrawScope.drawDashedLine(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dist = sqrt(dx * dx + dy * dy)
    if (dist == 0f) return
    val ux = dx / dist
    val uy = dy / dist
    var covered = 0f
    val dash = 10f
    val gap = 6f
    while (covered < dist) {
        val segmentEnd = (covered + dash).coerceAtMost(dist)
        drawLine(
            color = color,
            start = Offset(start.x + ux * covered, start.y + uy * covered),
            end = Offset(start.x + ux * segmentEnd, start.y + uy * segmentEnd),
            strokeWidth = strokeWidth,
        )
        covered = segmentEnd + gap
    }
}

/**
 * Contenido de la caja cuando no se muestra el WebView: URL y tamaño, más el aviso de estado o el
 * error de carga con su botón de reintentar.
 */
@Composable
private fun Placeholder(
    widget: Widget,
    touchSize: Size,
    viewState: BoxViewState,
    webLoadFailed: Boolean,
    onRetry: () -> Unit,
) {
    val density = LocalDensity.current.density
    val captionColor = if (viewState == BoxViewState.PartialOut) PartialBorder else TotalBorder

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (widget.url.isBlank()) "(sin URL)" else widget.url,
            style = MaterialTheme.typography.bodyMedium,
            color = TextColor,
            maxLines = 2,
        )
        Text(
            text = "${touchSize.width.roundToInt()} × ${touchSize.height.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MutedTextColor,
        )

        if (webLoadFailed) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error de carga",
                modifier = Modifier.size(24.dp),
                tint = TotalBorder,
            )
            Text(
                text = "Error al cargar la vista previa",
                style = MaterialTheme.typography.bodySmall,
                color = TotalBorder,
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reintentar",
                )
            }
        } else {
            val caption = when (viewState) {
                BoxViewState.PartialOut -> "⚠ fuera del panel (se recortará en el stream)"
                BoxViewState.TotalOut -> "fuera del panel (no se verá)"
                BoxViewState.MissingUrl -> "falta URL"
                BoxViewState.Normal, BoxViewState.Selected -> null
            }
            caption?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = captionColor,
                )
            }
        }
    }
}

/**
 * Una caja editable dentro del [Panel].
 *
 * El gesto vive en px táctiles con estado `remember` claveado por `widget.id`, así el arrastre
 * sobrevive a las recomposiciones del Panel (p. ej. las guías de snap por frame). El commit a
 * px-layout ocurre en `onDragEnd`/`onResizeEnd`; `onDragCancel`/`onResizeCancel` descarta el
 * estado local y deja el último commit válido.
 *
 * El estado fuera-del-panel (`outOfPanel`) se calcula desde la posición táctil **en vivo**
 * (`touchPosState`), así el borde amarillo/rojo y el ⚠ siguen al dedo en tiempo real durante el
 * arrastre, y reflejan la posición commitada cuando no hay gesto en curso.
 */
@Composable
fun WidgetBox(
    widget: Widget,
    selected: Boolean,
    isDragging: Boolean,
    showWebPreview: Boolean,
    touchPosition: WidgetPosition,
    touchSize: Size,
    panelDims: PanelDims,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onUrlEdit: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: (Float, Float) -> Unit,
    onDragCancel: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: (Float, Float) -> Unit,
    onResizeCancel: () -> Unit,
) {
    val density = LocalDensity.current.density
    val touchPosState = remember(widget.id) { mutableStateOf(touchPosition) }
    val touchSizeState = remember(widget.id) { mutableStateOf(touchSize) }
    val dragStartPosState = remember(widget.id) { mutableStateOf<WidgetPosition?>(null) }
    val webLoadFailedState = remember(widget.id) { mutableStateOf(false) }
    val webViewRefState = remember(widget.id) { mutableStateOf<WebView?>(null) }

    // Se resincroniza con el modelo (posición/commit) cuando no hay gesto en curso.
    LaunchedEffect(widget.id, isDragging) {
        if (!isDragging) {
            touchPosState.value = touchPosition
            touchSizeState.value = touchSize
        }
    }
    // Estado fuera-del-panel sobre la posición táctil en vivo (sigue al dedo durante el drag).
    val panelW = panelDims.width.toFloat()
    val panelH = panelDims.height.toFloat()
    val out = outOfPanel(
        x = touchPosState.value.x,
        y = touchPosState.value.y,
        w = touchSizeState.value.width,
        h = touchSizeState.value.height,
        panelW = panelW,
        panelH = panelH,
    )

    val viewState = boxViewState(widget, out, selected)

    val borderModifier = if (viewState == BoxViewState.PartialOut) Modifier
    else Modifier.border(borderWidth(viewState), borderColor(viewState), RoundedCornerShape(2.dp))

    val gestureModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                dragStartPosState.value = WidgetPosition(touchPosState.value.x, touchPosState.value.y)
                onDragStart()
            },
            onDrag = { _: PointerInputChange, dragAmount: Offset ->
                touchPosState.value = WidgetPosition(
                    x = touchPosState.value.x + dragAmount.x,
                    y = touchPosState.value.y + dragAmount.y,
                )
                onDrag(touchPosState.value.x, touchPosState.value.y)
            },
            onDragEnd = {
                onDragEnd(touchPosState.value.x, touchPosState.value.y)
            },
            onDragCancel = {
                dragStartPosState.value?.let { touchPosState.value = it }
                onDragCancel()
            },
        )
    }

    val handlePx = HandleSizeDp * density
    val handleOffsetX = ((touchSizeState.value.width - handlePx) / density).coerceAtLeast(0f).dp
    val handleOffsetY = ((touchSizeState.value.height - handlePx) / density).coerceAtLeast(0f).dp

    Box(
        modifier = Modifier
            .offset(
                x = (touchPosState.value.x / density).dp,
                y = (touchPosState.value.y / density).dp,
            )
            .width((touchSizeState.value.width / density).dp)
            .height((touchSizeState.value.height / density).dp)
            .background(WidgetBg)
            .then(borderModifier)
            .clickable(enabled = !isDragging, onClick = onSelect)
            .then(gestureModifier)
    ) {
        // Borde discontinuo para fuera parcial (dibujado bajo el contenido).
        if (viewState == BoxViewState.PartialOut) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2f * density
                val w = size.width
                val h = size.height
                drawDashedLine(Offset(0f, 0f), Offset(w, 0f), PartialBorder, stroke)
                drawDashedLine(Offset(w, 0f), Offset(w, h), PartialBorder, stroke)
                drawDashedLine(Offset(0f, h), Offset(w, h), PartialBorder, stroke)
                drawDashedLine(Offset(0f, 0f), Offset(0f, h), PartialBorder, stroke)
            }
        }

        val previewActive =
            showWebPreview && selected && !isDragging && widget.url.isNotBlank() && !webLoadFailedState.value

        // Navega a la URL solo cuando el preview está activo y hay URL: no se relanza por cada
        // recomposición (p. ej. guías de snap por frame durante un arrastre del Panel). Se relanza
        // al cambiar la URL o al activarse/desactivarse el preview, por lo que cubre la vuelta de
        // webLoadFailed (previewActive vuelve a true al reintentar y recarga la URL retenida).
        LaunchedEffect(widget.url, previewActive) {
            if (previewActive && widget.url.isNotBlank()) {
                webLoadFailedState.value = false
                webViewRefState.value?.loadUrl(widget.url)
            }
        }

        if (previewActive) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        setOnTouchListener { _, _ -> true }
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                super.onReceivedError(view, request, error)
                                // Solo un fallo del frame principal invalida la preview; un
                                // favicon/script caído no la mata.
                                if (request.isForMainFrame) {
                                    webLoadFailedState.value = true
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    webViewRefState.value = webView
                },
            )
        } else {
            Placeholder(
                widget = widget,
                touchSize = touchSizeState.value,
                viewState = viewState,
                webLoadFailed = webLoadFailedState.value,
                onRetry = {
                    // Reintento real sobre el WebView retenido.
                    webViewRefState.value?.reload()
                    webLoadFailedState.value = false
                },
            )
        }

        if (selected && !isDragging && !previewActive) {
            // Edición in-situ de la URL de la caja seleccionada (Task 6): se commitea al
            // ViewModel vía onUrlEdit; el ✕ elimina la caja. Mientras hay preview web no se edita.
            Box(
                modifier = Modifier.offset(4.dp, 4.dp),
            ) {
                Row(
                    modifier = Modifier,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = widget.url,
                        onValueChange = { onUrlEdit(it) },
                        singleLine = true,
                        label = { Text("URL") },
                        modifier = Modifier.width(140.dp)
                    )
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Eliminar widget",
                        )
                    }
                }
            }
        }

        if (selected) {
            // Handle de rediseño en la esquina inferior-derecha: hijo separado declarado después
            // del cuerpo (encima), con su propio gesto de arrastre con prioridad de consumo.
            Box(
                modifier = Modifier
                    .offset(handleOffsetX, handleOffsetY)
                    .size(HandleSizeDp.dp)
                    .background(HandleColor, RoundedCornerShape(HandleSizeDp.dp))
                    .border(1.dp, HandleBorder, RoundedCornerShape(HandleSizeDp.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onResizeStart() },
                            onDrag = { _: PointerInputChange, dragAmount: Offset ->
                                touchSizeState.value = Size(
                                    width = (touchSizeState.value.width + dragAmount.x).coerceAtLeast(0f),
                                    height = (touchSizeState.value.height + dragAmount.y).coerceAtLeast(0f),
                                )
                                onResize(touchSizeState.value.width, touchSizeState.value.height)
                            },
                            onDragEnd = {
                                onResizeEnd(touchSizeState.value.width, touchSizeState.value.height)
                            },
                            onDragCancel = { onResizeCancel() },
                        )
                    },
            ) {}
        }
    }
}

/**
 * Panel táctil de un layout: contenedor fijo con aspect-ratio 16:9 / 9:16 dentro del cual los
 * widgets se arrastran para mover y se estiran desde el handle de la esquina inferior-derecha.
 *
 * Los gestos viven en px táctiles (estado local); `onMove`/`onResize` emiten ya convertidos a
 * px-layout vía [PanelMapper], por lo que el ViewModel no convierte nada.
 *
 * El tamaño del panel se lee vía `onSizeChanged` y se guarda en estado; solo se usan widgets
 * cuando hay dimensione > 0, evitando NaN en PanelMapper.
 */
@Composable
fun Panel(
    layout: Layout,
    widgets: List<Widget>,
    snapEnabled: Boolean,
    showWebPreview: Boolean,
    selectedId: String?,
    onWidgetSelected: (String?) -> Unit,
    onMove: (id: String, layoutX: Float, layoutY: Float) -> Unit,
    onResize: (id: String, layoutW: Int, layoutH: Int) -> Unit,
    onUrlEdit: (id: String, url: String) -> Unit,
    onRemove: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutSize = PanelMapper.layoutSizeFor(layout)
    val aspectRatio = when (layout) {
        Layout.HORIZONTAL -> 16f / 9f
        Layout.VERTICAL -> 9f / 16f
    }

    val panelDimsState = remember { mutableStateOf<PanelDims?>(null) }
    val draggingIdState = remember { mutableStateOf<String?>(null) }
    val dragGuidesState = remember { mutableStateOf<Set<Guideline>>(emptySet()) }
    val dragGuideSizeState = remember { mutableStateOf<WidgetSize>(WidgetSize(0, 0)) }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(PanelBg)
            .border(1.dp, PanelBorder, RoundedCornerShape(4.dp))
            .clickable { onWidgetSelected(null) }
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    panelDimsState.value = PanelDims(size.width, size.height)
                }
            },
    ) {
        val panelDims = panelDimsState.value
        if (panelDims == null) return@Box

        GuidesOverlay(
            guides = dragGuidesState.value,
            widgetSize = dragGuideSizeState.value,
            layout = layout,
        )

        widgets.forEach { widget ->
            val selected = selectedId == widget.id
            val isDragging = draggingIdState.value == widget.id
            val touchPosition = PanelMapper.toTouchPosition(widget.position, panelDims, layout)
            val touchSize = Size(
                width = widget.size.width.toFloat() * panelDims.width / layoutSize.width,
                height = widget.size.height.toFloat() * panelDims.height / layoutSize.height,
            )

            WidgetBox(
                widget = widget,
                selected = selected,
                isDragging = isDragging,
                showWebPreview = showWebPreview,
                touchPosition = touchPosition,
                touchSize = touchSize,
                panelDims = panelDims,
                onSelect = { onWidgetSelected(widget.id) },
                onRemove = { onRemove(widget.id) },
                onUrlEdit = { nuevoTexto -> onUrlEdit(widget.id, nuevoTexto) },
                onDragStart = { draggingIdState.value = widget.id },
                onDrag = { x, y ->
                    draggingIdState.value = widget.id
                    dragGuideSizeState.value = widget.size
                    val predicted = PanelMapper.toLayoutPosition(x, y, panelDims, layout)
                    dragGuidesState.value = SnapHelper.snap(
                        position = predicted,
                        size = widget.size,
                        layout = layout,
                        enabled = snapEnabled,
                    ).guides
                },
                onDragEnd = { x, y ->
                    draggingIdState.value = null
                    dragGuidesState.value = emptySet()
                    val committed = PanelMapper.toLayoutPosition(x, y, panelDims, layout)
                    onMove(widget.id, committed.x, committed.y)
                },
                onDragCancel = {
                    draggingIdState.value = null
                    dragGuidesState.value = emptySet()
                },
                onResizeStart = { draggingIdState.value = widget.id },
                onResize = { w, h ->
                    draggingIdState.value = widget.id
                    dragGuideSizeState.value = PanelMapper.toLayoutSize(w, h, panelDims, layout)
                    dragGuidesState.value = SnapHelper.snap(
                        position = widget.position,
                        size = dragGuideSizeState.value,
                        layout = layout,
                        enabled = snapEnabled,
                    ).guides
                },
                onResizeEnd = { w, h ->
                    draggingIdState.value = null
                    dragGuidesState.value = emptySet()
                    val committedSize = PanelMapper.toLayoutSize(w, h, panelDims, layout)
                    onResize(widget.id, committedSize.width, committedSize.height)
                },
                onResizeCancel = {
                    draggingIdState.value = null
                    dragGuidesState.value = emptySet()
                },
            )
        }
    }
}
