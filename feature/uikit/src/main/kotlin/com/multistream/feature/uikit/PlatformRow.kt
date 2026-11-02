package com.multistream.feature.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Fila de plataforma: marcador con el color de marca, nombre en el color de marca y
 * chip de estado ([status], normalmente [StatusChip]).
 *
 * El color de marca identifica, no comunica estado: el estado va en [status], que es
 * punto + texto (nunca solo color). Altura táctil mínima de 48 dp.
 */
@Composable
fun PlatformRow(
    name: String,
    brandColor: Color,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = brandColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                color = brandColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(12.dp))
            status()
        }
    }
}

@Preview(name = "phone", showBackground = true, backgroundColor = 0xFF0B1526)
@Composable
private fun PlatformRowPhonePreview() {
    MultistreamTheme {
        PreviewColumn {
            PlatformRow(
                name = "Twitch",
                brandColor = Color(0xFF9146FF),
                status = { StatusChip(text = "Live", active = true) },
                onClick = {}
            )
            PlatformRow(
                name = "YouTube",
                brandColor = Color(0xFFFF0033),
                status = { StatusChip(text = "Offline", active = false) }
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
private fun PlatformRowTabletPreview() {
    MultistreamTheme {
        PreviewColumn {
            PlatformRow(
                name = "Twitch",
                brandColor = Color(0xFF9146FF),
                status = { StatusChip(text = "Live", active = true) },
                onClick = {}
            )
            PlatformRow(
                name = "Kick",
                brandColor = Color(0xFF53FC18),
                status = { StatusChip(text = "Offline", active = false) }
            )
        }
    }
}
