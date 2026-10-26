package com.multistream.data.platforms

import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials

/**
 * Authenticates against a streaming platform and yields the ingest credentials the streamer
 * needs (RTMP URL + stream key).
 *
 * Implementations must be safe to call repeatedly: a successful [login] result should be
 * reusable without forcing the user through the platform's authorization again.
 */
interface PlatformAuth {
    suspend fun login(): PlatformCredentials
}

/**
 * Base class for platforms that support OAuth 2.0 (Twitch, YouTube, Facebook).
 *
 * [login] first asks [SecureCredentialStore] for previously stored credentials and returns them
 * unchanged, so an authenticated user never repeats the authorization. Only when nothing is
 * stored does it fall back to [simulateOAuthFlow].
 *
 * The full OAuth 2.0 authorization-code flow is out of scope for the MVP: it needs a registered
 * client id per platform, a redirect URI handled by a browser/custom tab and, for YouTube and
 * Facebook, verified scopes and an app review. Until that exists, [simulateOAuthFlow] returns the
 * platform's default ingest URL with an empty stream key, which the UI completes through
 * [ManualAuth] once the user pastes their key.
 */
abstract class OAuthPlatformAuth internal constructor(
    private val credentialStore: SecureCredentialStore,
    protected val platform: Platform,
    private val fallbackRtmpUrl: String
) : PlatformAuth {

    final override suspend fun login(): PlatformCredentials {
        credentialStore.load(platform)?.let { return it }
        return simulateOAuthFlow()
    }

    /**
     * MVP stand-in for the OAuth 2.0 exchange. Override when the real flow is implemented; the
     * default returns the platform's ingest URL with no stream key.
     */
    protected open suspend fun simulateOAuthFlow(): PlatformCredentials =
        PlatformCredentials(
            platform = platform,
            rtmpUrl = fallbackRtmpUrl,
            streamKey = ""
        )
}
