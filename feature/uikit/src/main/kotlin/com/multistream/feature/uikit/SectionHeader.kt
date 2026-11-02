package com.multistream.feature.uikit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/**
 * Cabecera de sección del hub: texto corto en mayúsculas con el tono muted del spec.
 * El texto lo pasa el caller; el componente no fija tamaño de tipo, así que escala
 * con la fuente del sistema sin recortarse.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Preview(name = "phone", showBackground = true, backgroundColor = 0xFF0B1526)
@Composable
private fun SectionHeaderPhonePreview() {
    MultistreamTheme {
        PreviewColumn {
            SectionHeader(text = "Connected Platforms")
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
private fun SectionHeaderTabletPreview() {
    MultistreamTheme {
        PreviewColumn {
            SectionHeader(text = "Connected Platforms")
        }
    }
}
