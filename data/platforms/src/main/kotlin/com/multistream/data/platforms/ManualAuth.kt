package com.multistream.data.platforms

import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual RTMP entry: the user pastes the ingest URL and stream key provided by the platform.
 *
 * This is the fallback used while OAuth 2.0 is not implemented, and the only authenticator that
 * accepts arbitrary values (including [Platform.CUSTOM] endpoints).
 *
 * It does not implement [PlatformAuth] because [PlatformAuth.login] takes no arguments: the values
 * to store come from the user, not from an authorization exchange.
 */
@Singleton
class ManualAuth @Inject constructor(
    private val credentialStore: SecureCredentialStore
) {

    /**
     * Builds [PlatformCredentials] from the values entered by the user, stores them encrypted in
     * [SecureCredentialStore] (replacing any previous entry for [platform]) and returns them.
     */
    suspend fun login(
        platform: Platform,
        rtmpUrl: String,
        streamKey: String
    ): PlatformCredentials {
        val credentials = PlatformCredentials(
            platform = platform,
            rtmpUrl = rtmpUrl,
            streamKey = streamKey
        )
        credentialStore.save(credentials)
        return credentials
    }
}
