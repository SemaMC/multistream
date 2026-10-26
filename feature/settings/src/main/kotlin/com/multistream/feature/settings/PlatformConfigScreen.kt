package com.multistream.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Platform

/**
 * Lists the supported streaming platforms and triggers the authentication flow of the selected
 * one through [PlatformConfigViewModel].
 */
@Composable
fun PlatformConfigScreen(
    viewModel: PlatformConfigViewModel = hiltViewModel()
) {
    val platforms by viewModel.platforms.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Platform Configuration",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        platforms.forEach { platform ->
            PlatformConfigCard(
                platform = platform,
                onConfigure = { viewModel.configurePlatform(platform) }
            )
        }
    }
}

/** Card for one platform with the button that starts its configuration. */
@Composable
fun PlatformConfigCard(
    platform: Platform,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(platform.name)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onConfigure) {
                Text("Configure")
            }
        }
    }
}
