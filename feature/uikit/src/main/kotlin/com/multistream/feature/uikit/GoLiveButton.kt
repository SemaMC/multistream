package com.multistream.feature.uikit

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Botón principal "GO LIVE": relleno con primary (= Accent #3D9BFF), altura táctil
 * mínima de 48 dp. `enabled = false` usa el estado deshabilitado del tema; el caller
 * comunica el motivo en el texto, no solo por color.
 */
@Composable
fun GoLiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Preview(name = "phone", showBackground = true, backgroundColor = 0xFF0B1526)
@Composable
private fun GoLiveButtonPhonePreview() {
    MultistreamTheme {
        PreviewColumn {
            GoLiveButton(text = "GO LIVE", onClick = {}, modifier = Modifier.fillMaxWidth())
            GoLiveButton(
                text = "CONNECT FIRST",
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
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
private fun GoLiveButtonTabletPreview() {
    MultistreamTheme {
        PreviewColumn {
            GoLiveButton(text = "GO LIVE", onClick = {}, modifier = Modifier.fillMaxWidth())
            GoLiveButton(
                text = "CONNECT FIRST",
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
