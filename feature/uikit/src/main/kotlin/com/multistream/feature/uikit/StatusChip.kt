package com.multistream.feature.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Chip de estado: punto + texto. El estado se comunica dos veces (color del punto y el
 * propio texto que pasa el caller), nunca solo por color.
 *
 * - [active]: punto en Ok (#34D17B) frente a muted; el texto siempre legible.
 * - [dotColor]/[textColor] override permiten expresiones extra (error, etc.) sin nuevo estado.
 */
@Composable
fun StatusChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    textColor: Color? = null
) {
    val resolvedDot = dotColor
        ?: if (active) MultistreamColors.Ok else MaterialTheme.colorScheme.onSurfaceVariant
    val resolvedText = textColor
        ?: if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .height(26.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = resolvedDot, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = resolvedText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview(name = "phone", showBackground = true, backgroundColor = 0xFF0B1526)
@Composable
private fun StatusChipPhonePreview() {
    MultistreamTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(text = "Live", active = true)
            StatusChip(text = "Offline", active = false)
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
private fun StatusChipTabletPreview() {
    MultistreamTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(text = "Live", active = true)
            StatusChip(text = "Offline", active = false)
        }
    }
}
