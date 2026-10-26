package com.multistream.core.encoding

import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceMonitorTest {

    private lateinit var mockContext: Context
    private lateinit var mockPowerManager: PowerManager
    private lateinit var monitor: PerformanceMonitor

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPowerManager = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns mockPowerManager
        monitor = PerformanceMonitor(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `PerformanceMonitor starts in NORMAL state with full bitrate`() {
        monitor.startMonitoring()

        val recommendation = monitor.getRecommendation()

        assertFalse(recommendation.shouldReduceBitrate)
        assertEquals(1.0f, recommendation.bitrateMultiplier)
        assertFalse(recommendation.shouldDropFrames)
    }

    @Test
    fun `PerformanceMonitor detects thermal throttling and recommends bitrate reduction`() {
        monitor.startMonitoring()

        // Simulate thermal throttling
        monitor.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE)

        val recommendation = monitor.getRecommendation()
        assertTrue(recommendation.shouldReduceBitrate)
        assertTrue(recommendation.bitrateMultiplier < 1.0f)
    }

    @Test
    fun `MODERATE thermal status recommends 0_8 bitrate multiplier without frame dropping`() {
        monitor.onThermalStatusChanged(PowerManager.THERMAL_STATUS_MODERATE)

        val recommendation = monitor.getRecommendation()

        assertFalse(recommendation.shouldReduceBitrate)
        assertEquals(0.8f, recommendation.bitrateMultiplier)
        assertFalse(recommendation.shouldDropFrames)
    }

    @Test
    fun `SEVERE thermal status recommends 0_6 bitrate multiplier without frame dropping`() {
        monitor.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE)

        val recommendation = monitor.getRecommendation()

        assertTrue(recommendation.shouldReduceBitrate)
        assertEquals(0.6f, recommendation.bitrateMultiplier)
        assertFalse(recommendation.shouldDropFrames)
    }

    @Test
    fun `CRITICAL thermal status recommends 0_4 bitrate multiplier with frame dropping`() {
        monitor.onThermalStatusChanged(PowerManager.THERMAL_STATUS_CRITICAL)

        val recommendation = monitor.getRecommendation()

        assertTrue(recommendation.shouldReduceBitrate)
        assertEquals(0.4f, recommendation.bitrateMultiplier)
        assertTrue(recommendation.shouldDropFrames)
    }

    @Test
    fun `shouldDropFrames is false for every status below CRITICAL`() {
        val belowCritical = listOf(
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE,
            PowerManager.THERMAL_STATUS_SEVERE
        )

        belowCritical.forEach { status ->
            monitor.onThermalStatusChanged(status)
            assertFalse(
                "shouldDropFrames must be false for status $status",
                monitor.getRecommendation().shouldDropFrames
            )
        }
    }
}
