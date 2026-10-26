package com.multistream.data.platforms

import com.multistream.core.common.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube authentication.
 *
 * A real integration needs Google's OAuth 2.0 flow with the verified `youtube.force-ssl` scope,
 * whose app review takes weeks; the MVP returns YouTube's default ingest URL and relies on manual
 * key entry (see [OAuthPlatformAuth]).
 *
 * Ingest URL: `rtmp://a.rtmp.youtube.com/live2/`
 */
@Singleton
class YouTubeAuth @Inject constructor(
    credentialStore: SecureCredentialStore
) : OAuthPlatformAuth(
    credentialStore = credentialStore,
    platform = Platform.YOUTUBE,
    fallbackRtmpUrl = RTMP_URL
)

private const val RTMP_URL = "rtmp://a.rtmp.youtube.com/live2/"
