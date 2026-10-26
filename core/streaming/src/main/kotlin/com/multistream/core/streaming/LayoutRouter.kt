package com.multistream.core.streaming

import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import javax.inject.Inject
import javax.inject.Singleton

interface LayoutRouter {
    fun getLayout(platform: Platform): Layout
}

@Singleton
class DefaultLayoutRouter @Inject constructor() : LayoutRouter {
    override fun getLayout(platform: Platform): Layout {
        return when (platform) {
            Platform.TWITCH, Platform.YOUTUBE, Platform.KICK, Platform.FACEBOOK, Platform.CUSTOM -> Layout.HORIZONTAL
        }
    }
}
