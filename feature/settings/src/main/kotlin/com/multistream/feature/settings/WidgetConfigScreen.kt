package com.multistream.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget

/**
 * Shows the configured overlay widgets grouped by the layout they belong to, and lets the user add
 * or remove them so the horizontal (16:9) and vertical (9:16) compositions stay independent.
 */
@Composable
fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel = hiltViewModel()
) {
    val horizontalWidgets by viewModel.horizontalWidgets.collectAsState()
    val verticalWidgets by viewModel.verticalWidgets.collectAsState()

    var addingLayout by remember { mutableStateOf<Layout?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            widgets = horizontalWidgets,
            onAdd = { addingLayout = Layout.HORIZONTAL },
            onRemove = viewModel::removeWidget
        )

        Spacer(modifier = Modifier.height(16.dp))

        WidgetSection(
            title = "Vertical Layout Widgets",
            widgets = verticalWidgets,
            onAdd = { addingLayout = Layout.VERTICAL },
            onRemove = viewModel::removeWidget
        )
    }

    addingLayout?.let { layout ->
        AddWidgetDialog(
            layout = layout,
            onSave = { url, x, y, width, height ->
                viewModel.addWidget(layout, url, x, y, width, height)
                addingLayout = null
            },
            onCancel = { addingLayout = null }
        )
    }
}

/** One layout group: its title, an add button, an empty state and one card per widget. */
@Composable
private fun WidgetSection(
    title: String,
    widgets: List<Widget>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
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
                onRemove = { onRemove(widget.id) }
            )
        }
    }
}

/** Summary of one overlay widget with the button that removes it. */
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
                Text(widget.url)
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
 * Asks for the overlay URL, its position and its size. Saving stays blocked until the URL is not
 * blank and the numbers parse, so an unusable widget is never stored.
 */
@Composable
private fun AddWidgetDialog(
    layout: Layout,
    onSave: (url: String, x: Float, y: Float, width: Int, height: Int) -> Unit,
    onCancel: () -> Unit
) {
    var url by remember(layout) { mutableStateOf("") }
    var x by remember(layout) { mutableStateOf("0") }
    var y by remember(layout) { mutableStateOf("0") }
    var width by remember(layout) {
        mutableStateOf(if (layout == Layout.VERTICAL) "1080" else "1920")
    }
    var height by remember(layout) {
        mutableStateOf(if (layout == Layout.VERTICAL) "1920" else "1080")
    }

    val parsedX = x.trim().toFloatOrNull()
    val parsedY = y.trim().toFloatOrNull()
    val parsedWidth = width.trim().toIntOrNull()
    val parsedHeight = height.trim().toIntOrNull()
    val canSave = url.isNotBlank() &&
        parsedX != null && parsedY != null &&
        parsedWidth != null && parsedWidth > 0 &&
        parsedHeight != null && parsedHeight > 0

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
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = x,
                        onValueChange = { x = it },
                        label = "X",
                        isDecimal = true,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = y,
                        onValueChange = { y = it },
                        label = "Y",
                        isDecimal = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = width,
                        onValueChange = { width = it },
                        label = "Width",
                        isDecimal = false,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = height,
                        onValueChange = { height = it },
                        label = "Height",
                        isDecimal = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        url,
                        parsedX ?: 0f,
                        parsedY ?: 0f,
                        parsedWidth ?: 0,
                        parsedHeight ?: 0
                    )
                },
                enabled = canSave
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

/** Single-line numeric field: decimal for coordinates, integer for sizes. */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isDecimal: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier
    )
}
