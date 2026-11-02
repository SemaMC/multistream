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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.multistream.core.common.Layout
import com.multistream.feature.uikit.GoLiveButton
import com.multistream.feature.uikit.SectionHeader
import com.multistream.feature.uikit.StatusChip

/**
 * Pantalla de streaming adaptativa por [currentWindowAdaptiveInfo]: en `Expanded` (tablet) los
 * previews y el bloque de controles/estado van lado a lado en [Row]; en el resto de anchos
 * (Compact/Medium) sigue la columna unica de [Column]. Ambas variantes aplican sus propios
 * insets (regla de Task 4: cada pantalla pone su padding, `safeDrawingPadding` nunca en el
 * scaffold padre).
 *
 * El volcado de texto de widgets se sustituye por [WidgetSummary] (resumen con conteo); la
 * colocacion completa sigue en [LayoutPreview].
 *
 * Logica de sesion intacta: Start pide el token de consent de MediaProjection y, concedido,
 * abre la sesion de las plataformas con credenciales ya guardadas
 * (ver [StreamingViewModel.platforms]).
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

    val startStopLabel = when {
        isStreaming -> "Stop"
        isStarting -> "Starting..."
        else -> "Start"
    }

    val onStartStop = {
        if (isStreaming) {
            viewModel.stopStreaming()
        } else {
            captureLauncher.launch(viewModel.screenCaptureIntent())
        }
    }
    val startStopEnabled = isStreaming || (!isStarting && platforms.isNotEmpty())

    val isExpanded = currentWindowAdaptiveInfo()
        .windowSizeClass
        .windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = streamingLabel,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LayoutPreview(
                        layout = Layout.HORIZONTAL,
                        widgets = horizontalWidgets,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LayoutPreview(
                        layout = Layout.VERTICAL,
                        widgets = verticalWidgets,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StreamingControls(
                        startStopLabel = startStopLabel,
                        startStopEnabled = startStopEnabled,
                        onStartStop = onStartStop,
                        platformsCount = platforms.size,
                        errorMessage = errorMessage
                    )

                    WidgetSummary(
                        horizontalCount = horizontalWidgets.size,
                        verticalCount = verticalWidgets.size
                    )
                }
            }
        } else {
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StreamingControls(
                    startStopLabel = startStopLabel,
                    startStopEnabled = startStopEnabled,
                    onStartStop = onStartStop,
                    platformsCount = platforms.size,
                    errorMessage = errorMessage
                )

                WidgetSummary(
                    horizontalCount = horizontalWidgets.size,
                    verticalCount = verticalWidgets.size
                )
            }
        }
    }
}

/**
 * Bloque de controles/estado (mismo contrato que la pantalla anterior): Start/Stop con 48 dp de
 * altura tactil via [GoLiveButton], estado de plataformas y error en texto (nunca solo color).
 */
@Composable
private fun StreamingControls(
    startStopLabel: String,
    startStopEnabled: Boolean,
    onStartStop: () -> Unit,
    platformsCount: Int,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GoLiveButton(
            text = startStopLabel,
            onClick = onStartStop,
            enabled = startStopEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (platformsCount == 0) {
            Text(
                text = "No platforms configured. Add one in Settings first.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = "Streaming to $platformsCount platform(s)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

/**
 * Resumen con conteo que sustituye el volcado de texto de widgets: dos chips (uno por layout)
 * apilados en [Column], que no se recortan ni a 320 dp ni con la fuente al 200%.
 */
@Composable
private fun WidgetSummary(
    horizontalCount: Int,
    verticalCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "Widget Configuration")

        Spacer(modifier = Modifier.height(8.dp))

        StatusChip(text = "Horizontal: $horizontalCount widgets", active = false)
        Spacer(modifier = Modifier.height(8.dp))
        StatusChip(text = "Vertical: $verticalCount widgets", active = false)
    }
}
