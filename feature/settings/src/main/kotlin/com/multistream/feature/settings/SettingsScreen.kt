package com.multistream.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.navigation.NavController
import androidx.window.core.layout.WindowWidthSizeClass
import com.multistream.feature.uikit.SectionHeader
import com.multistream.feature.uikit.SettingRow

/**
 * Las dos opciones de Configuracion. Cada id conmuta un editor ya existente en el panel de
 * detalle ([PlatformConfigScreen] / [WidgetConfigScreen]); aqui solo se reparten filas.
 */
private data class SettingsOption(
    val id: String,
    val title: String,
    val icon: ImageVector
)

private val SettingsOptions = listOf(
    SettingsOption(
        id = "platforms",
        title = "Platforms",
        icon = Icons.Filled.Layers
    ),
    SettingsOption(
        id = "widgets",
        title = "Widgets",
        icon = Icons.Filled.Widgets
    )
)

/**
 * Pantalla de Configuracion two-pane: opciones Plataformas/Widgets a la izquierda y, a la
 * derecha, el editor correspondiente embebido tal cual ([PlatformConfigScreen] /
 * [WidgetConfigScreen] obtienen su ViewModel por `hiltViewModel()` por si mismos; aqui no se
 * duplican ni se toca el `Panel`).
 *
 * El primer tap en una opcion muestra su editor directamente a la derecha: sin segundo boton
 * ni pagina intermedia (pin del Review Focus). Rotacion: [rememberSaveable] mantiene la
 * seleccion, sin `android:configChanges`.
 *
 * La flecha de arriba a la izquierda vuelve al destino anterior con [NavController.popBackStack];
 * en ningun caso se navega a una pantalla fantasma.
 *
 * En Compact no hay dos paneles: opciones y detalle se apilan en una [Column] con scroll y la
 * opcion seleccionada nunca navega fuera; este fichero es la pantalla completa.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    var selectedOptionId by rememberSaveable { mutableStateOf<String?>(null) }

    val isExpanded = currentWindowAdaptiveInfo()
        .windowSizeClass
        .windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        if (isExpanded) {
            SettingsTwoPane(
                selectedOptionId = selectedOptionId,
                onSelectOption = { optionId -> selectedOptionId = optionId },
                modifier = Modifier.weight(1f)
            )
        } else {
            SettingsStacked(
                selectedOptionId = selectedOptionId,
                onSelectOption = { optionId -> selectedOptionId = optionId },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Variante Expanded: lista de opciones con peso fijo a la izquierda y el detalle ocupando el
 * resto. El contenido del detalle es auto-scrollable (LazyColumn / verticalScroll de los
 * editores), por lo que aqui NO se le anade un scroll externo: solo se le da una altura
 * acotada, que es lo que esos scrollers internos necesitan.
 */
@Composable
private fun SettingsTwoPane(
    selectedOptionId: String?,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        SettingsOptionList(
            selectedOptionId = selectedOptionId,
            onSelectOption = onSelectOption,
            modifier = Modifier.width(340.dp)
        )

        SettingsDetailPane(
            selectedOptionId = selectedOptionId,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

/**
 * Variante Compact: opciones arriba y detalle debajo apilados en una [Column] con scroll,
 * sin paginas intermedias. Los editores embebidos son scrollers internos y no pueden medirse
 * con altura infinita (LazyColumn crashea), asi que ocupan un viewport de altura fija dentro
 * del scroll externo: el editor navega por dentro y el scroll externo sigue al llegar al borde.
 */
@Composable
private fun SettingsStacked(
    selectedOptionId: String?,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsOptionList(
            selectedOptionId = selectedOptionId,
            onSelectOption = onSelectOption,
            modifier = Modifier.fillMaxWidth()
        )

        SettingsDetailPane(
            selectedOptionId = selectedOptionId,
            modifier = if (selectedOptionId != null) {
                Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        )
    }
}

/**
 * Lista de opciones de Configuracion. La fila marcada lleva el acento de [SettingRow] y no
 * depende solo del color (borde + titulo/icono tintados).
 */
@Composable
private fun SettingsOptionList(
    selectedOptionId: String?,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(text = "Options")

        SettingsOptions.forEach { option ->
            SettingRow(
                title = option.title,
                icon = option.icon,
                selected = option.id == selectedOptionId,
                onClick = { onSelectOption(option.id) },
                trailing = {}
            )
        }
    }
}

/**
 * Panel de detalle: el editor embebido de la opcion seleccionada, sin envoltorios que
 * dupliquen su comportamiento. Sin seleccion, placeholder explicito: nunca una pantalla vacia.
 */
@Composable
private fun SettingsDetailPane(
    selectedOptionId: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (selectedOptionId) {
            "platforms" -> Box(modifier = Modifier.safeDrawingPadding()) {
                PlatformConfigScreen()
            }
            "widgets" -> WidgetConfigScreen()
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(text = "Nothing selected")

                    Text(
                        text = "Select an option from the list to configure it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
