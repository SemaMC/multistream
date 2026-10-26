package com.multistream.core.streaming

import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutRouterTest {
    @Test
    fun `LayoutRouter returns HORIZONTAL for TWITCH`() {
        val router = DefaultLayoutRouter()
        assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.TWITCH))
    }

    @Test
    fun `LayoutRouter returns HORIZONTAL for YOUTUBE`() {
        val router = DefaultLayoutRouter()
        assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.YOUTUBE))
    }

    @Test
    fun `LayoutRouter returns HORIZONTAL for KICK`() {
        val router = DefaultLayoutRouter()
        assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.KICK))
    }

    @Test
    fun `LayoutRouter returns HORIZONTAL for FACEBOOK`() {
        val router = DefaultLayoutRouter()
        assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.FACEBOOK))
    }

    @Test
    fun `LayoutRouter returns HORIZONTAL for CUSTOM`() {
        val router = DefaultLayoutRouter()
        assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.CUSTOM))
    }
}
