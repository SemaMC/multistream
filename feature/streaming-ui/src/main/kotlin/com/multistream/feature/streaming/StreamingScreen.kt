package com.multistream.feature.streamingui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import com.multistream.feature.uikit.GoLiveButton
import com.multistream.feature.uikit.MultistreamColors
import com.multistream.feature.uikit.StatusChip

/**
 * Pantalla Go Live compacta (Task 4): todo el contenido visible en una pantalla, sin el
 * scroll largo de la v1.
 *
 * Estructura (decision 4 del spec): la vertical es LA preview de Go Live (la horizontal y
 * la vertical del editor viven en Configuracion), reducida al area central; encima flotan
 * los conteos (como chips) y los botones (tuerca y atajo a widgets); el control Start/Stop
 * flota al pie de la preview; debajo queda la barra inferior de estado con una fila por
 * plataforma (dot verde = configurada / rojo = pendiente) + nombre. El estado va en punto
 * y texto, nunca en color solo.
 *
 * La tuerca y el acceso directo a la edicion de widgets navegan a la pantalla de
 * configuracion via [onOpenConfiguration]: la ruta `configuracion` es propiedad de
 * AppNavigation y este feature no la conoce.
 *
 * Logica de sesion intacta: [StreamingViewModel] no cambia (Start pide el consent de
 * MediaProjection y, concedido, abre la sesion de las plataformas con credenciales ya
 * guardadas).
 */
@Composable
fun StreamingScreen(
    onOpenConfiguration: () -> Unit,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isStarting by viewModel.isStarting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val configuredPlatforms by viewModel.platforms.collectAsState()
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
    val startStopEnabled = isStreaming || (!isStarting && configuredPlatforms.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Titulo + estado de la sesion como chip (texto y punto, nunca solo color).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Go Live",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.weight(1f))

            StatusChip(text = streamingLabel, active = isStreaming)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Preview vertical de Go Live. `fillMaxHeight` deja el min-width en 0 para que
            // aspectRatio (9/16) resuelva por altura: tamanno exacto, centrado; en Compact
            // nunca supera el ancho del parent (sin recorte ni scroll horizontal).
            LayoutPreview(
                layout = Layout.VERTICAL,
                widgets = verticalWidgets,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
            )

            // Conteos como chips flotantes, apilados para no romper el ancho de la
            // preview vertical ni en 360 dp ni con la fuente al maximo.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    text = "Horizontal: ${horizontalWidgets.size} widgets",
                    active = false
                )
                StatusChip(
                    text = "Vertical: ${verticalWidgets.size} widgets",
                    active = false
                )
            }

            // Botones flotantes: la tuerca abre Configuracion y el atajo lleva a la
            // edicion de widgets (misma pantalla de Configuracion).
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Open configuration",
                    tint = MultistreamColors.Accent,
                    onClick = onOpenConfiguration
                )
                FloatingIconButton(
                    icon = Icons.Filled.Widgets,
                    contentDescription = "Edit widgets",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onOpenConfiguration
                )
            }

            // Controles flotantes al pie de la preview: Start/Stop y, si hay error, el
            // mensaje en texto (nunca solo color).
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GoLiveButton(
                        text = startStopLabel,
                        onClick = onStartStop,
                        enabled = startStopEnabled
                    )

                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra inferior de estado: una fila por plataforma soportada, con el nombre;
        // con cero configuradas, ademas, una linea explica que falta hacer.
        PlatformStatusBar(configuredPlatforms = configuredPlatforms)
    }
}

/**
 * Boton flotante circular sobre la preview: icono con tinte sobre la superficie del tema.
 * [IconButton] garantiza el objetivo tactil de 48 dp; [contentDescription] lleva el
 * significado para el lector de pantalla (el icono no es texto en pantalla).
 */
@Composable
private fun FloatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape
        )
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

/**
 * Barra inferior de estado: una fila por plataforma soportada (la misma carga
 * [StreamingViewModel]: Twitch, YouTube, Kick, Facebook). Dot verde del token Ok cuando
 * las credenciales ya estan guardadas y rojo (color de error del tema) cuando esta
 * pendiente; la fila repite el estado en texto ("Ready"/"Pending"), asi el color nunca es
 * la unica senal. Si `platforms` trajera en el futuro una plataforma fuera de ese
 * conjunto, se annade al final como fila extra.
 *
 * Con cero plataformas configuradas las cuatro filas salen en rojo/pendiente y aparece la
 * linea de ayuda: lista no vacia sin explicacion, como pide el Review Focus.
 */
@Composable
private fun PlatformStatusBar(
    configuredPlatforms: List<PlatformConfig>
) {
    val supported: List<Platform> = listOf(
        Platform.TWITCH,
        Platform.YOUTUBE,
        Platform.KICK,
        Platform.FACEBOOK
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (configuredPlatforms.isEmpty()) {
                Text(
                    text = "No platforms configured yet. Add one in Settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            supported.forEach { platform ->
                val configured = configuredPlatforms.any { it.platform == platform }
                PlatformStatusBarRow(
                    name = platformName(platform),
                    statusText = if (configured) "Ready" else "Pending",
                    configured = configured
                )
            }

            configuredPlatforms
                .filter { it.platform !in supported }
                .forEach { config ->
                    PlatformStatusBarRow(
                        name = platformName(config.platform),
                        statusText = "Ready",
                        configured = true
                    )
                }
        }
    }
}

/** Una fila de la barra de estado: dot + nombre + estado en texto, sin color solo. */
@Composable
private fun PlatformStatusBarRow(
    name: String,
    statusText: String,
    configured: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (configured) MultistreamColors.Ok else MaterialTheme.colorScheme.error,
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = statusText,
            color = if (configured) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Nombre visible de cada [Platform]; `CUSTOM` es el endpoint RTMP a medida. */
private fun platformName(platform: Platform): String = when (platform) {
    Platform.TWITCH -> "Twitch"
    Platform.YOUTUBE -> "YouTube"
    Platform.KICK -> "Kick"
    Platform.FACEBOOK -> "Facebook"
    Platform.CUSTOM -> "Custom"
}
