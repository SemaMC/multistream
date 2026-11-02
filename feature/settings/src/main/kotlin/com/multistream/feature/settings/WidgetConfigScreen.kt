package com.multistream.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget

/**
 * Shows the configured overlay widgets grouped by the layout they belong to, and lets the user add
 * or remove them so the horizontal (16:9) and vertical (9:16) compositions stay independent.
 *
 * Each layout is a tactile [Panel]: widgets are dragged to move and stretched from the corner
 * handle to resize. Toggles ("Imán" / "Vista previa web") and the selected widget are kept with
 * [rememberSaveable], so they survive recreation (e.g. rotation); the widget layout itself lives in
 * the Singleton WidgetLayoutManager and survives recreation.
 */
@Composable
fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel = hiltViewModel()
) {
    val horizontalWidgets by viewModel.horizontalWidgets.collectAsState()
    val verticalWidgets by viewModel.verticalWidgets.collectAsState()

    // Widget seleccionado por layout: el dado de alta nace seleccionado en su layout.
    var selectedHorizontal by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVertical by rememberSaveable { mutableStateOf<String?>(null) }

    // Toggles por layout.
    var snapHorizontal by rememberSaveable { mutableStateOf(true) }
    var snapVertical by rememberSaveable { mutableStateOf(true) }
    var webPreviewHorizontal by rememberSaveable { mutableStateOf(false) }
    var webPreviewVertical by rememberSaveable { mutableStateOf(false) }

    var addingLayout by remember { mutableStateOf<Layout?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Widget Configuration",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        WidgetSection(
            title = "Horizontal Layout Widgets",
            layout = Layout.HORIZONTAL,
            widgets = horizontalWidgets,
            selectedId = selectedHorizontal,
            onSelectionChange = { selectedHorizontal = it },
            snapEnabled = snapHorizontal,
            onSnapChange = { snapHorizontal = it },
            showWebPreview = webPreviewHorizontal,
            onPreviewChange = { webPreviewHorizontal = it },
            onAdd = { addingLayout = Layout.HORIZONTAL },
            onRemove = { viewModel.removeWidget(it) },
            onMove = { id, x, y -> viewModel.moveWidget(id, x, y, snapHorizontal) },
            onResize = { id, w, h -> viewModel.resizeWidget(id, w, h) },
            onUrlEdit = { id, url -> viewModel.setWidgetUrl(id, url) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        WidgetSection(
            title = "Vertical Layout Widgets",
            layout = Layout.VERTICAL,
            widgets = verticalWidgets,
            selectedId = selectedVertical,
            onSelectionChange = { selectedVertical = it },
            snapEnabled = snapVertical,
            onSnapChange = { snapVertical = it },
            showWebPreview = webPreviewVertical,
            onPreviewChange = { webPreviewVertical = it },
            onAdd = { addingLayout = Layout.VERTICAL },
            onRemove = { viewModel.removeWidget(it) },
            onMove = { id, x, y -> viewModel.moveWidget(id, x, y, snapVertical) },
            onResize = { id, w, h -> viewModel.resizeWidget(id, w, h) },
            onUrlEdit = { id, url -> viewModel.setWidgetUrl(id, url) },
        )
    }

    addingLayout?.let { layout ->
        AddWidgetDialog(
            layout = layout,
            onSave = { url ->
                // El widget nace centrado (ViewModel) y se selecciona al instante para editar su URL in-situ.
                val id = viewModel.addWidget(layout, url)
                when (layout) {
                    Layout.HORIZONTAL -> selectedHorizontal = id
                    Layout.VERTICAL -> selectedVertical = id
                }
                addingLayout = null
            },
            onCancel = { addingLayout = null }
        )
    }
}

/** One layout group: its title, an add button, the "Imán" / "Vista previa web" toggles, the
 * tactile [Panel] and one card per widget below it. */
@Composable
private fun WidgetSection(
    title: String,
    layout: Layout,
    widgets: List<Widget>,
    selectedId: String?,
    onSelectionChange: (String?) -> Unit,
    snapEnabled: Boolean,
    onSnapChange: (Boolean) -> Unit,
    showWebPreview: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onUrlEdit: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onAdd) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = snapEnabled,
                onCheckedChange = onSnapChange
            )
            Text(text = "Imán", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = showWebPreview,
                onCheckedChange = onPreviewChange
            )
            Text(text = "Vista previa web", style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Panel(
        layout = layout,
        widgets = widgets,
        snapEnabled = snapEnabled,
        showWebPreview = showWebPreview,
        selectedId = selectedId,
        onWidgetSelected = onSelectionChange,
        onMove = onMove,
        onResize = onResize,
        onUrlEdit = onUrlEdit,
        // El ✕ de la caja in-panel también debe deseleccionar si borra al seleccionado,
        // para no dejar la selección huérfana (equivalente al ✕ de la tarjeta).
        onRemove = { id ->
            if (selectedId == id) onSelectionChange(null)
            onRemove(id)
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (widgets.isEmpty()) {
        Text(
            text = "No widgets configured",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        widgets.forEach { widget ->
            WidgetConfigCard(
                widget = widget,
                onRemove = {
                    // Si se borra el widget seleccionado vía ✕, no dejar la selección huérfana.
                    if (selectedId == widget.id) onSelectionChange(null)
                    onRemove(widget.id)
                }
            )
        }
    }
}

/** Summary of one overlay widget with the button that removes it. A blank URL is marked in red
 * ("falta URL") so widgets lost outside the panel stay recoverable from this list. */
@Composable
fun WidgetConfigCard(
    widget: Widget,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (widget.url.isBlank()) "falta URL" else widget.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (widget.url.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text("Position: (${widget.position.x}, ${widget.position.y})")
                Text("Size: ${widget.size.width}x${widget.size.height}")
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove widget"
                )
            }
        }
    }
}

/**
 * Asks for the overlay URL only. The widget is born centred (480x270) via
 * [WidgetConfigViewModel.addWidget], so there are no X/Y/Width/Height fields; an empty URL is
 * allowed and shows as "falta URL" in the panel and the card list.
 */
@Composable
private fun AddWidgetDialog(
    layout: Layout,
    onSave: (url: String) -> Unit,
    onCancel: () -> Unit
) {
    var url by remember(layout) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add widget (${layout.name.lowercase()})") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Overlay URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(url)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
