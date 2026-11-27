package com.example.myweibo

import com.example.myweibo.ui.VideoChromeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoChromeStyleTest {
    @Test fun bothSlidersAtZeroSkipEffectsButKeepHighContrastSurface() {
        val style = VideoChromeStyle(frostAmount = 0f, glassAmount = 0f)

        assertFalse(style.hasEffects)
        assertEquals(0f, style.blurDp, 0f)
        assertEquals(0f, style.refraction, 0f)
        assertTrue(style.surfaceAlpha(effectsApplied = false) >= 0.68f)
    }

    @Test fun slidersControlBlurAndRefractionIndependently() {
        val frostOnly = VideoChromeStyle(frostAmount = 0.5f, glassAmount = 0f)
        val glassOnly = VideoChromeStyle(frostAmount = 0f, glassAmount = 0.75f)
        val combined = VideoChromeStyle(frostAmount = 1f, glassAmount = 0.25f)

        assertTrue(frostOnly.hasEffects)
        assertEquals(12f, frostOnly.blurDp, 0f)
        assertEquals(0f, frostOnly.refraction, 0f)
        assertTrue(glassOnly.hasEffects)
        assertEquals(0f, glassOnly.blurDp, 0f)
        assertEquals(0.75f, glassOnly.refraction, 0f)
        assertEquals(24f, combined.blurDp, 0f)
        assertEquals(0.25f, combined.refraction, 0f)
        assertTrue(combined.surfaceAlpha(effectsApplied = true) > glassOnly.surfaceAlpha(effectsApplied = true))
        assertEquals(glassOnly.surfaceAlpha(effectsApplied = false), combined.surfaceAlpha(effectsApplied = false), 0f)
    }

    @Test fun malformedPreferencesDoNotReachTheCompositor() {
        assertFalse(VideoChromeStyle(Float.NaN, -1f).hasEffects)
        assertFalse(VideoChromeStyle(0f, Float.POSITIVE_INFINITY).hasEffects)
        val clamped = VideoChromeStyle(2f, 3f)
        assertEquals(24f, clamped.blurDp, 0f)
        assertEquals(1f, clamped.refraction, 0f)
    }
}
