package com.multistream.feature.streamingui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import com.multistream.core.common.toConfig
import com.multistream.data.platforms.SecureCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes the platforms whose credentials are already stored, so [HomeScreen] can list only the
 * ones the user has configured.
 *
 * Reading credentials is suspending and decrypts from [SecureCredentialStore], which is why the
 * load runs in [viewModelScope] and the UI observes the resulting [StateFlow].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val credentialStore: SecureCredentialStore
) : ViewModel() {

    private val _platforms = MutableStateFlow<List<PlatformConfig>>(emptyList())
    val platforms: StateFlow<List<PlatformConfig>> = _platforms.asStateFlow()

    init {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            val platforms = listOf(
                credentialStore.load(Platform.TWITCH),
                credentialStore.load(Platform.YOUTUBE),
                credentialStore.load(Platform.KICK),
                credentialStore.load(Platform.FACEBOOK)
            ).filterNotNull().map { it.toConfig() }

            _platforms.value = platforms
        }
    }
}
