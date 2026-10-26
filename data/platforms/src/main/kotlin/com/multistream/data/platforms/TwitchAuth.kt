package com.multistream.data.platforms

import com.multistream.core.common.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Twitch authentication.
 *
 * A real integration needs Twitch's OAuth 2.0 authorization-code flow with the
 * `channel:read:stream_key` scope to obtain the stream key; the MVP returns Twitch's default
 * ingest URL and relies on manual key entry (see [OAuthPlatformAuth]).
 *
 * Ingest URL: `rtmp://live.twitch.tv/app/`
 */
@Singleton
class TwitchAuth @Inject constructor(
    credentialStore: SecureCredentialStore
) : OAuthPlatformAuth(
    credentialStore = credentialStore,
    platform = Platform.TWITCH,
    fallbackRtmpUrl = RTMP_URL
)

private const val RTMP_URL = "rtmp://live.twitch.tv/app/"
