package com.multistream.feature.uikit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Fila de ajustes del hub: icono + título y un slot [trailing] libre (chip, switch, texto).
 *
 * Reglas del componente:
 * - altura táctil mínima de 48 dp;
 * - el icono lleva siempre `contentDescription` no nulo ([iconDescription], por defecto el
 *   propio título), cumpliendo "contentDescription en todo icono";
 * - deshabilitada: `enabled = false` anula el click y baja la opacidad del título, y el
 *   caller comunica el motivo en el texto de [trailing] ("Soon"), no solo por color.
 */
@Composable
fun SettingRow(
    title: String,
    icon: ImageVector,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    iconDescription: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        modifier = rowModifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription ?: title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .alpha(if (enabled) 1f else 0.5f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Preview(name = "phone", showBackground = true, backgroundColor = 0xFF0B1526)
@Composable
private fun SettingRowPhonePreview() {
    MultistreamTheme {
        PreviewColumn {
            SettingRow(
                title = "Streaming",
                icon = Icons.Filled.Cast,
                onClick = {},
                trailing = { StatusChip(text = "Live", active = true) }
            )
            SettingRow(
                title = "Alerts",
                icon = Icons.Filled.VideoCall,
                trailing = { StatusChip(text = "Soon", active = false) },
                enabled = false
            )
        }
    }
}

@Preview(
    name = "tablet",
    showBackground = true,
    backgroundColor = 0xFF0B1526,
    widthDp = 800,
    heightDp = 600
)
@Composable
private fun SettingRowTabletPreview() {
    MultistreamTheme {
        PreviewColumn {
            SettingRow(
                title = "Streaming",
                icon = Icons.Filled.Cast,
                onClick = {},
                trailing = { StatusChip(text = "Live", active = true) }
            )
            SettingRow(
                title = "Account",
                icon = Icons.Filled.Settings,
                trailing = { Text(text = "SemaMC", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }
    }
}
