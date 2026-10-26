package com.multistream.feature.streamingui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Layout

/**
 * Dual layout streaming screen: both previews side by side, one Start/Stop control and the widget
 * configuration of each layout.
 *
 * Start requests the MediaProjection consent token and, once granted, opens the session for the
 * platforms whose credentials are already stored (see [StreamingViewModel.platforms]).
 */
@Composable
fun StreamingScreen(
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isStarting by viewModel.isStarting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val platforms by viewModel.platforms.collectAsState()
    val horizontalWidgets by viewModel.horizontalWidgets.collectAsState()
    val verticalWidgets by viewModel.verticalWidgets.collectAsState()

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onScreenCaptureResult(result.resultCode, result.data)
    }

    val streamingLabel = when {
        isStreaming -> "Streaming..."
        isStarting -> "Starting..."
        else -> "Ready to stream"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = streamingLabel,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LayoutPreview(
                layout = Layout.HORIZONTAL,
                widgets = horizontalWidgets,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            LayoutPreview(
                layout = Layout.VERTICAL,
                widgets = verticalWidgets,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isStreaming) {
                    viewModel.stopStreaming()
                } else {
                    captureLauncher.launch(viewModel.screenCaptureIntent())
                }
            },
            enabled = isStreaming || (!isStarting && platforms.isNotEmpty())
        ) {
            Text(
                when {
                    isStreaming -> "Stop"
                    isStarting -> "Starting..."
                    else -> "Start"
                }
            )
        }

        if (platforms.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No platforms configured. Add one in Settings first.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Streaming to ${platforms.size} platform(s)",
                style = MaterialTheme.typography.bodySmall
            )
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Widget Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        Text("Horizontal Layout (${horizontalWidgets.size} widgets)")
        horizontalWidgets.forEach { widget ->
            Text("  - ${widget.url} at (${widget.position.x}, ${widget.position.y})")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Vertical Layout (${verticalWidgets.size} widgets)")
        verticalWidgets.forEach { widget ->
            Text("  - ${widget.url} at (${widget.position.x}, ${widget.position.y})")
        }
    }
}
