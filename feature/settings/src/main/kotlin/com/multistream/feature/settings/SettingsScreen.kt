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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.navigation.NavController
import com.multistream.feature.uikit.SectionHeader
import com.multistream.feature.uikit.SettingRow
import com.multistream.feature.uikit.StatusChip

/**
 * Seccion del hub de Ajustes. [route] no nulo = tiene pantalla de configuracion a la que
 * navegar; nulo = aun sin backend, se muestra deshabilitada con "Soon".
 */
private data class SettingsSection(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
    val route: String?
)

private val SettingsSections = listOf(
    SettingsSection(
        id = "account",
        title = "Account",
        icon = Icons.Filled.AccountCircle,
        description = "Manage your streaming accounts.",
        route = null
    ),
    SettingsSection(
        id = "multistream",
        title = "Multistream",
        icon = Icons.Filled.Layers,
        description = "Connect the platforms to broadcast to.",
        route = "settings/platforms"
    ),
    SettingsSection(
        id = "streaming",
        title = "Streaming",
        icon = Icons.Filled.Cast,
        description = "Bitrate, resolution and encoder settings.",
        route = null
    ),
    SettingsSection(
        id = "alerts",
        title = "Alerts",
        icon = Icons.Filled.Notifications,
        description = "Followers, donations and raid alerts.",
        route = null
    ),
    SettingsSection(
        id = "events",
        title = "Events",
        icon = Icons.Filled.Event,
        description = "Replay recent stream events.",
        route = null
    ),
    SettingsSection(
        id = "chat",
        title = "Chat",
        icon = Icons.AutoMirrored.Filled.Chat,
        description = "Combined chat across platforms.",
        route = null
    ),
    SettingsSection(
        id = "widgets",
        title = "Widgets",
        icon = Icons.Filled.Widgets,
        description = "Arrange the layout widgets of the overlay.",
        route = "settings/widgets"
    )
)

/**
 * Hub de Ajustes two-pane estilo Streamlabs: lista de secciones y, en `Expanded` (tablet),
 * panel de detalle al lado; con seccion sin seleccionar muestra un placeholder, nunca una
 * pantalla vacia. En Compact la lista ocupa el ancho completo y las secciones con backend
 * navegan directamente a sus rutas existentes ("settings/platforms" y "settings/widgets").
 *
 * La seleccion sobrevive a rotacion y re-creacion del proceso: [rememberSaveable], sin
 * `android:configChanges`. Sin backend aun: fila deshabilitada con chip "Soon", nunca solo
 * por color (el chip lleva el texto y la fila baja de opacidad).
 */
@Composable
fun SettingsScreen(navController: NavController) {
    var selectedSectionId by rememberSaveable { mutableStateOf<String?>(null) }

    val isExpanded = currentWindowAdaptiveInfo()
        .windowSizeClass
        .windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsSectionList(
                    modifier = Modifier.width(340.dp),
                    onItemClick = { section -> selectedSectionId = section.id }
                )

                SettingsDetailPane(
                    section = SettingsSections.firstOrNull { it.id == selectedSectionId },
                    onOpen = { section -> section.route?.let(navController::navigate) },
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            }
        } else {
            SettingsSectionList(
                modifier = Modifier.fillMaxWidth(),
                onItemClick = { section -> section.route?.let(navController::navigate) }
            )
        }
    }
}

/**
 * Lista de secciones del hub. En [itemClick] cae la politica: seleccionar en Expanded,
 * navegar en Compact; el caller decide, la lista solo reparte filas.
 */
@Composable
private fun SettingsSectionList(
    modifier: Modifier,
    onItemClick: (SettingsSection) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionHeader(text = "Sections") }

        items(
            items = SettingsSections,
            key = { it.id }
        ) { section ->
            val enabled = section.route != null
            val click = if (enabled) ({ onItemClick(section) }) else null

            SettingRow(
                title = section.title,
                icon = section.icon,
                onClick = click,
                enabled = enabled,
                trailing = {
                    if (enabled) {
                        StatusChip(text = "Available", active = true)
                    } else {
                        StatusChip(text = "Soon", active = false)
                    }
                }
            )
        }
    }
}

/**
 * Panel de detalle de la variante Expanded: con seleccion muestra titulo, descripcion y,
 * si la seccion tiene backend, el acceso a su pantalla de configuracion; sin seleccion
 * placeholder explicito (pin del Review Focus: nada de pantalla vacia ni crash).
 */
@Composable
private fun SettingsDetailPane(
    section: SettingsSection?,
    onOpen: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (section == null) {
            SectionHeader(text = "Nothing selected")

            Text(
                text = "Select a section from the list to configure it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            SectionHeader(text = "Section")

            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = section.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )

            if (section.route != null) {
                Button(
                    onClick = { onOpen(section) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Open configuration")
                }
            }
        }
    }
}
