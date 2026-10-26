package com.multistream.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Widget

/**
 * Shows the configured overlay widgets grouped by the layout they belong to, so the horizontal
 * (16:9) and vertical (9:16) compositions can be reviewed separately.
 */
@Composable
fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel = hiltViewModel()
) {
    val horizontalWidgets by viewModel.horizontalWidgets.collectAsState()
    val verticalWidgets by viewModel.verticalWidgets.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Widget Configuration",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Horizontal Layout Widgets",
            style = MaterialTheme.typography.titleMedium
        )
        horizontalWidgets.forEach { widget ->
            WidgetConfigCard(widget = widget)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Vertical Layout Widgets",
            style = MaterialTheme.typography.titleMedium
        )
        verticalWidgets.forEach { widget ->
            WidgetConfigCard(widget = widget)
        }
    }
}

/** Read-only summary of one overlay widget. */
@Composable
fun WidgetConfigCard(widget: Widget) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(widget.url)
            Text("Position: (${widget.position.x}, ${widget.position.y})")
            Text("Size: ${widget.size.width}x${widget.size.height}")
        }
    }
}
