package com.multistream.data.platforms

import com.multistream.core.common.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facebook Live authentication.
 *
 * A real integration needs Facebook's OAuth 2.0 flow with the `publish_video` permission, which
 * requires an app review; the MVP returns Facebook's default ingest URL and relies on manual key
 * entry (see [OAuthPlatformAuth]).
 *
 * Facebook only accepts RTMPS (RTMP over TLS) on port 443, so the URL scheme must stay `rtmps://`.
 */
@Singleton
class FacebookAuth @Inject constructor(
    credentialStore: SecureCredentialStore
) : OAuthPlatformAuth(
    credentialStore = credentialStore,
    platform = Platform.FACEBOOK,
    fallbackRtmpUrl = RTMP_URL
)

private const val RTMP_URL = "rtmps://live-api-s.facebook.com:443/rtmp/"
