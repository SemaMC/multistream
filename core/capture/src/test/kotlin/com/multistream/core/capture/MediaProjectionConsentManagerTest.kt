package com.multistream.core.capture

import android.content.Intent
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MediaProjectionConsentManagerTest {
    
    private lateinit var consentManager: MediaProjectionConsentManager
    private lateinit var mockContext: android.content.Context
    private lateinit var mockProjectionManager: android.media.projection.MediaProjectionManager
    
    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockProjectionManager = mockk(relaxed = true)
        
        every { mockContext.getSystemService(any()) } returns mockProjectionManager
        every { mockProjectionManager.createScreenCaptureIntent() } returns Intent()
        
        consentManager = MediaProjectionConsentManager(mockContext)
    }
    
    @After
    fun tearDown() {
        consentManager.revokeConsent()
        unmockkAll()
    }
    
    @Test
    fun `initial state has no consent`() = runBlocking {
        assertFalse(consentManager.isConsentGranted.first())
        assertNull(consentManager.getMediaProjection())
    }
    
    @Test
    fun `createConsentIntent returns intent`() {
        val intent = consentManager.createConsentIntent()
        assertNotNull(intent)
    }
    
    @Test
    fun `isConsentValid returns false when no consent`() {
        assertFalse(consentManager.isConsentValid())
    }
    
    @Test
    fun `revokeConsent clears state`() {
        consentManager.revokeConsent()
        
        assertFalse(consentManager.isConsentValid())
        assertNull(consentManager.getMediaProjection())
    }
}
