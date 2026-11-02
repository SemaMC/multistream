package com.multistream.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multistream.core.common.Platform
import com.multistream.feature.uikit.MultistreamColors
import com.multistream.feature.uikit.PlatformRow
import com.multistream.feature.uikit.StatusChip

/** One configurable platform with the label and brand color the uikit row renders. */
private data class ConfigPlatform(
    val platform: Platform,
    val label: String,
    val brandColor: Color
)

/**
 * The same 5 platforms [PlatformConfigViewModel] starts with, in the same order, with the brand
 * colors already used by the Home dashboard. Brand colors identify; the state goes in the chip
 * (dot + text), never only in color. [Platform.CUSTOM] has no brand: it takes the accent token.
 */
private val ConfigPlatforms = listOf(
    ConfigPlatform(Platform.TWITCH, "Twitch", Color(0xFF9146FF)),
    ConfigPlatform(Platform.YOUTUBE, "YouTube", Color(0xFFFF0033)),
    ConfigPlatform(Platform.KICK, "Kick", Color(0xFF53FC18)),
    ConfigPlatform(Platform.FACEBOOK, "Facebook", Color(0xFF0866FF)),
    ConfigPlatform(Platform.CUSTOM, "Custom", MultistreamColors.Accent)
)

private fun configPlatformOf(platform: Platform): ConfigPlatform =
    ConfigPlatforms.first { it.platform == platform }

/**
 * Lists the supported streaming platforms and drives the credentials dialog of the selected one
 * through [PlatformConfigViewModel].
 *
 * [PlatformRow] renders each row with its brand color; the chip says "Connected" or "Pending".
 * The ViewModel does not expose persisted platform state (its contract is untouched), so the
 * honest bound of the chip is the session: a platform counts as "Connected" from the first
 * successful save of this session, and the set survives recreation ([rememberSaveable], without
 * `android:configChanges`).
 */
@Composable
fun PlatformConfigScreen(
    viewModel: PlatformConfigViewModel = hiltViewModel()
) {
    val platforms by viewModel.platforms.collectAsState()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Platform whose dialog is open; only on Success does it feed the connected set, so a failed
    // save never marks its platform. rememberSaveable keeps it across recreation.
    var pendingPlatform by rememberSaveable { mutableStateOf<Platform?>(null) }
    var connectedPlatforms by rememberSaveable { mutableStateOf(setOf<Platform>()) }

    // Success and error are transient: announce them and return to Idle.
    LaunchedEffect(state) {
        when (val currentState = state) {
            is ConfigurationState.AwaitingStreamKey -> {
                pendingPlatform = currentState.platform
            }
            is ConfigurationState.Success -> {
                pendingPlatform?.let { platform ->
                    connectedPlatforms = connectedPlatforms + platform
                }
                pendingPlatform = null
                snackbarHostState.showSnackbar("Credentials saved")
                viewModel.resetState()
            }
            is ConfigurationState.Error -> {
                pendingPlatform = null
                snackbarHostState.showSnackbar(currentState.message)
                viewModel.resetState()
            }
            is ConfigurationState.Idle -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Platform Configuration",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            items(
                items = platforms,
                key = { it.name }
            ) { platform ->
                val configuredPlatform = configPlatformOf(platform)
                val connected = platform in connectedPlatforms

                PlatformRow(
                    name = configuredPlatform.label,
                    brandColor = configuredPlatform.brandColor,
                    status = {
                        StatusChip(
                            text = if (connected) "Connected" else "Pending",
                            active = connected
                        )
                    },
                    onClick = { viewModel.startConfiguration(platform) }
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

/**
 * Asks for the ingest URL and stream key of [platform]. The URL comes preloaded with the platform
 * default and stays editable because custom endpoints need it; saving is blocked until both fields
 * hold a value so an empty stream key is never persisted.
 *
 * The keyboard is treated: `imePadding` keeps the focused field visible while the IME is up and
 * the text column scrolls if the space runs out (320 dp or 200% font); the URL field gets an URI
 * keyboard (no autocorrect) and the key field a password one (dots, no suggestions).
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
        modifier = Modifier.imePadding(),
        title = { Text("Configure ${configPlatformOf(platform).label}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("RTMP URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream Key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
