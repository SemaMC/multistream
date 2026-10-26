package com.multistream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multistream.core.common.Platform
import com.multistream.data.platforms.FacebookAuth
import com.multistream.data.platforms.ManualAuth
import com.multistream.data.platforms.TwitchAuth
import com.multistream.data.platforms.YouTubeAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the platform list shown by [PlatformConfigScreen] and dispatches each platform's
 * authentication flow.
 *
 * The three OAuth platforms delegate to their [com.multistream.data.platforms.PlatformAuth]
 * implementations; [Platform.CUSTOM] uses [ManualAuth] because it accepts an arbitrary ingest URL
 * and stream key. OAuth flows are simulated for now (see `OAuthPlatformAuth`), so the credentials
 * they return are completed by the manual flow later.
 */
@HiltViewModel
class PlatformConfigViewModel @Inject constructor(
    private val twitchAuth: TwitchAuth,
    private val youtubeAuth: YouTubeAuth,
    private val facebookAuth: FacebookAuth,
    private val manualAuth: ManualAuth
) : ViewModel() {

    private val _platforms = MutableStateFlow(
        listOf(Platform.TWITCH, Platform.YOUTUBE, Platform.FACEBOOK, Platform.CUSTOM)
    )
    val platforms: StateFlow<List<Platform>> = _platforms.asStateFlow()

    fun configurePlatform(platform: Platform) {
        viewModelScope.launch {
            when (platform) {
                Platform.TWITCH -> twitchAuth.login()
                Platform.YOUTUBE -> youtubeAuth.login()
                Platform.FACEBOOK -> facebookAuth.login()
                Platform.CUSTOM -> manualAuth.login(platform, "", "")
                else -> { /* No authenticator for this platform yet. */ }
            }
        }
    }
}
