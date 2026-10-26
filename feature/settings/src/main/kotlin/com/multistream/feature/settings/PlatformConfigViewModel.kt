package com.multistream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multistream.core.common.Platform
import com.multistream.data.platforms.ManualAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the platform configuration flow exposed by [PlatformConfigViewModel] and rendered by
 * [PlatformConfigScreen].
 */
sealed interface ConfigurationState {
    /** No configuration in progress and no feedback to show. */
    object Idle : ConfigurationState

    /** The user picked [platform]; the screen asks for [defaultUrl] and a stream key. */
    data class AwaitingStreamKey(
        val platform: Platform,
        val defaultUrl: String
    ) : ConfigurationState

    /** The credentials were stored in [com.multistream.data.platforms.SecureCredentialStore]. */
    object Success : ConfigurationState

    /** Storing the credentials failed; [message] is shown to the user. */
    data class Error(val message: String) : ConfigurationState
}

/**
 * Owns the platform list shown by [PlatformConfigScreen] and the credentials dialog state.
 *
 * OAuth 2.0 is not implemented yet (see `OAuthPlatformAuth`), so its simulated result has an empty
 * stream key. Instead of persisting that unusable result, the screen always asks the user for the
 * ingest URL (preloaded with the platform default) and the stream key, and [ManualAuth] stores
 * them encrypted in `SecureCredentialStore`.
 */
@HiltViewModel
class PlatformConfigViewModel @Inject constructor(
    private val manualAuth: ManualAuth
) : ViewModel() {

    private val _platforms = MutableStateFlow(
        listOf(
            Platform.TWITCH,
            Platform.YOUTUBE,
            Platform.KICK,
            Platform.FACEBOOK,
            Platform.CUSTOM
        )
    )
    val platforms: StateFlow<List<Platform>> = _platforms.asStateFlow()

    private val _state = MutableStateFlow<ConfigurationState>(ConfigurationState.Idle)
    val state: StateFlow<ConfigurationState> = _state.asStateFlow()

    /** Moves to the credentials dialog for [platform] instead of discarding a simulated login. */
    fun startConfiguration(platform: Platform) {
        _state.value = ConfigurationState.AwaitingStreamKey(platform, defaultIngestUrl(platform))
    }

    /** Stores the values entered by the user and reports success or failure through [state]. */
    fun saveCredentials(platform: Platform, rtmpUrl: String, streamKey: String) {
        viewModelScope.launch {
            try {
                manualAuth.login(platform, rtmpUrl, streamKey)
                _state.value = ConfigurationState.Success
            } catch (e: Exception) {
                _state.value = ConfigurationState.Error(e.message ?: "Could not save credentials")
            }
        }
    }

    /** Dismisses the dialog and clears any feedback so the screen returns to [ConfigurationState.Idle]. */
    fun resetState() {
        _state.value = ConfigurationState.Idle
    }

    private fun defaultIngestUrl(platform: Platform): String = when (platform) {
        Platform.TWITCH -> "rtmp://live.twitch.tv/app/"
        Platform.YOUTUBE -> "rtmp://a.rtmp.youtube.com/live2/"
        Platform.KICK -> "rtmp://kick.livepush.com/live/"
        Platform.FACEBOOK -> "rtmps://live-api-s.facebook.com:443/rtmp/"
        Platform.CUSTOM -> ""
    }
}
