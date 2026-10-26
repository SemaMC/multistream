package com.multistream.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Platform

/**
 * Lists the supported streaming platforms and drives the credentials dialog of the selected one
 * through [PlatformConfigViewModel].
 */
@Composable
fun PlatformConfigScreen(
    viewModel: PlatformConfigViewModel = hiltViewModel()
) {
    val platforms by viewModel.platforms.collectAsState()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Success and error are transient: announce them and return to Idle.
    LaunchedEffect(state) {
        when (val currentState = state) {
            is ConfigurationState.Success -> {
                snackbarHostState.showSnackbar("Credentials saved")
                viewModel.resetState()
            }
            is ConfigurationState.Error -> {
                snackbarHostState.showSnackbar(currentState.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    onConfigure = { viewModel.startConfiguration(platform) }
                )
            }
        }
    }

    val awaitingStreamKey = state as? ConfigurationState.AwaitingStreamKey
    if (awaitingStreamKey != null) {
        CredentialsDialog(
            platform = awaitingStreamKey.platform,
            defaultUrl = awaitingStreamKey.defaultUrl,
            onSave = { rtmpUrl, streamKey ->
                viewModel.saveCredentials(awaitingStreamKey.platform, rtmpUrl, streamKey)
            },
            onCancel = viewModel::resetState
        )
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

/**
 * Asks for the ingest URL and stream key of [platform]. The URL comes preloaded with the platform
 * default and stays editable because custom endpoints need it; saving is blocked until both fields
 * hold a value so an empty stream key is never persisted.
 */
@Composable
private fun CredentialsDialog(
    platform: Platform,
    defaultUrl: String,
    onSave: (rtmpUrl: String, streamKey: String) -> Unit,
    onCancel: () -> Unit
) {
    var rtmpUrl by remember(platform) { mutableStateOf(defaultUrl) }
    var streamKey by remember(platform) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Configure ${platform.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("RTMP URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(rtmpUrl, streamKey) },
                enabled = rtmpUrl.isNotBlank() && streamKey.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
