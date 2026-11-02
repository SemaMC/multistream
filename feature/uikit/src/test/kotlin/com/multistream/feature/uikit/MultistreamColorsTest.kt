package com.multistream.feature.uikit

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MultistreamColorsTest {

    @Test
    fun background_esAzulNoche() {
        assertEquals(Color(0xFF0B1526), MultistreamColors.Background)
    }

    @Test
    fun surface_esAzulMedianoche() {
        assertEquals(Color(0xFF111F36), MultistreamColors.Surface)
    }

    @Test
    fun surfaceAlt_esAzulMedianocheAlterno() {
        assertEquals(Color(0xFF0E1B30), MultistreamColors.SurfaceAlt)
    }

    @Test
    fun outline_esLineaNocturna() {
        assertEquals(Color(0xFF1E2F4D), MultistreamColors.Outline)
    }

    @Test
    fun onBackground_esTextoClaro() {
        assertEquals(Color(0xFFE8EEF7), MultistreamColors.OnBackground)
    }

    @Test
    fun onBackgroundMuted_esTextoSecundario() {
        assertEquals(Color(0xFF93A4C0), MultistreamColors.OnBackgroundMuted)
    }

    @Test
    fun accent_esAzulElectrico() {
        assertEquals(Color(0xFF3D9BFF), MultistreamColors.Accent)
    }

    @Test
    fun ok_esVerdeDeSenal() {
        assertEquals(Color(0xFF34D17B), MultistreamColors.Ok)
    }
}
