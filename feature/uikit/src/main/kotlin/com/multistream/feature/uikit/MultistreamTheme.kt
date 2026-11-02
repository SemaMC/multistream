package com.multistream.feature.uikit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Único tema de la app: oscuro, sistema "azul noche". No existe variante clara.
 * Los tokens vienen de [MultistreamColors] y son los valores exactos del spec.
 */
private val NightColorScheme = darkColorScheme(
    primary = MultistreamColors.Accent,
    onPrimary = MultistreamColors.Background,
    secondary = MultistreamColors.OnBackgroundMuted,
    onSecondary = MultistreamColors.Background,
    tertiary = MultistreamColors.Ok,
    onTertiary = MultistreamColors.Background,
    background = MultistreamColors.Background,
    onBackground = MultistreamColors.OnBackground,
    surface = MultistreamColors.Surface,
    onSurface = MultistreamColors.OnBackground,
    surfaceVariant = MultistreamColors.SurfaceAlt,
    onSurfaceVariant = MultistreamColors.OnBackgroundMuted,
    outline = MultistreamColors.Outline,
    surfaceContainerLowest = MultistreamColors.SurfaceAlt,
    surfaceContainerLow = MultistreamColors.SurfaceAlt,
    surfaceContainer = MultistreamColors.Surface,
    surfaceContainerHigh = MultistreamColors.Surface,
    surfaceContainerHighest = MultistreamColors.Surface
)

/** Envuelve [content] en el único tema de la app (oscuro). */
@Composable
fun MultistreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColorScheme,
        content = content
    )
}

@Preview(name = "phone", showBackground = true, backgroundColor = 0xFF0B1526)
@Composable
private fun MultistreamThemePhonePreview() {
    MultistreamTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader("Connected Platforms")
            Text(text = "Multistream", style = MaterialTheme.typography.headlineSmall)
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
private fun MultistreamThemeTabletPreview() {
    MultistreamTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader("Connected Platforms")
            Text(text = "Multistream", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
