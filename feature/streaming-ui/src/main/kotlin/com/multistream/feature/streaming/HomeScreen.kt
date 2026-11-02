package com.multistream.feature.streamingui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.multistream.core.common.Platform
import com.multistream.feature.uikit.GoLiveButton
import com.multistream.feature.uikit.PlatformRow
import com.multistream.feature.uikit.SectionHeader
import com.multistream.feature.uikit.SettingRow
import com.multistream.feature.uikit.StatusChip

/**
 * Dashboard de inicio: hero con el estado de conexión, una [PlatformRow] por plataforma
 * (chip `Connected`/`Pending` según [HomeViewModel.platforms]) y el [GoLiveButton] que
 * abre la sesión de la primera plataforma conectada (`streaming/<PLATFORM>`).
 *
 * Las filas conectadas navegan a su ruta de streaming; las pendientes no son clicables
 * (`StreamingScreen` emite solo a plataformas con credenciales): conectarlas es cosa de
 * `settings`, que conserva su ruta como hasta ahora.
 *
 * El estado lo da el ViewModel (contrato intacto); nada nuevo que guardar aquí.
 */
private data class DashboardPlatform(
    val platform: Platform,
    val label: String,
    val brandColor: Color
)

/**
 * Las mismas 4 plataformas que [HomeViewModel] carga (una petición de `credentialStore`
 * por cada una, ver [HomeViewModel]). Los colores de marca identifican la plataforma;
 * el estado lo comunica el chip (punto + texto), nunca el color de marca.
 */
private val DashboardPlatforms = listOf(
    DashboardPlatform(Platform.TWITCH, "Twitch", Color(0xFF9146FF)),
    DashboardPlatform(Platform.YOUTUBE, "YouTube", Color(0xFFFF0033)),
    DashboardPlatform(Platform.KICK, "Kick", Color(0xFF53FC18)),
    DashboardPlatform(Platform.FACEBOOK, "Facebook", Color(0xFF0866FF))
)

/** Chip de estado de una fila del dashboard: conectada vs. pendiente. */
private fun platformStatusText(connected: Boolean): String =
    if (connected) "Connected" else "Pending"

/** Texto del hero: el estado también en texto, no solo en el color del punto. */
private fun heroStateText(connectedCount: Int): String =
    if (connectedCount == 0) {
        "No platforms connected"
    } else {
        "$connectedCount of ${DashboardPlatforms.size} platforms connected"
    }

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val connectedPlatforms by viewModel.platforms.collectAsState()
    val connectedPlatformsSet = connectedPlatforms.map { it.platform }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Multistream",
                    style = MaterialTheme.typography.headlineSmall
                )
                StatusChip(
                    text = heroStateText(connectedCount = connectedPlatforms.size),
                    active = connectedPlatforms.isNotEmpty()
                )
                GoLiveButton(
                    text = if (connectedPlatforms.isEmpty()) "CONNECT FIRST" else "GO LIVE",
                    enabled = connectedPlatforms.isNotEmpty(),
                    onClick = {
                        // enabled solo es true con plataformas conectadas: siempre hay first().
                        navController.navigate(
                            "streaming/${connectedPlatforms.first().platform.name}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionHeader(text = "Platforms")

        DashboardPlatforms.forEach { platform ->
            val connected = platform.platform in connectedPlatformsSet
            PlatformRow(
                name = platform.label,
                brandColor = platform.brandColor,
                status = {
                    StatusChip(text = platformStatusText(connected), active = connected)
                },
                onClick = if (connected) {
                    { navController.navigate("streaming/${platform.platform.name}") }
                } else {
                    null
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingRow(
            title = "Settings",
            icon = Icons.Filled.Settings,
            onClick = { navController.navigate("settings") },
            trailing = {
                Text(
                    text = "Manage platforms",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}
