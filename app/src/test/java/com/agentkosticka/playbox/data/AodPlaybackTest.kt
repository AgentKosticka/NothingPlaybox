package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.*
import org.junit.Assert.*
import org.junit.Test

class AodPlaybackTest {
    private fun effect(id: String, value: Int) = PlayboxEffect(id = id, name = id, frames = listOf(EffectFrame(IntArray(PIXEL_COUNT) { value })))
    @Test fun brightnessPauseAndQuietHoursAffectOutput() {
        val effects = listOf(effect("a", 200))
        assertEquals(100, AodPlayback(effects, "a", AodSettings(brightness = .5f)).frameAt(0, 12).pixels[0])
        assertTrue(AodPlayback(effects, "a", AodSettings(enabled = false)).frameAt(0, 12).pixels.all { it == 0 })
        val quiet = AodPlayback(effects, "a", AodSettings(quietHours = true))
        assertTrue(quiet.frameAt(0, 23).pixels.all { it == 0 })
        assertTrue(quiet.frameAt(100, 6).pixels.all { it == 0 })
        assertEquals(200, quiet.frameAt(200, 7).pixels[0])
    }
    @Test fun rotationChangesOnBoundaryAndSkipsMissingIds() {
        val playback = AodPlayback(listOf(effect("a", 100), effect("b", 200)), "a", AodSettings(rotate = true, rotationSeconds = 10, rotationIds = setOf("a", "b", "deleted")))
        assertEquals(100, playback.frameAt(9999, 12).pixels[0])
        assertEquals(200, playback.frameAt(10000, 12).pixels[0])
        assertEquals(100, playback.frameAt(20000, 12).pixels[0])
    }
    @Test fun emptyRotationFallsBackAndSpeedUsesFrameTiming() {
        val animated = effect("a", 20).copy(frames = listOf(EffectFrame(IntArray(PIXEL_COUNT) { 20 }, 200), EffectFrame(IntArray(PIXEL_COUNT) { 80 }, 200)))
        val playback = AodPlayback(listOf(animated), "a", AodSettings(rotate = true, speed = 2f))
        assertEquals(20, playback.frameAt(99, 12).pixels[0])
        assertEquals(80, playback.frameAt(100, 12).pixels[0])
    }
    @Test fun quietHoursHandleSameDayOvernightAndAllDay() {
        assertTrue(AodSettings(quietHours = true, quietStart = 9, quietEnd = 17).isQuiet(12))
        assertFalse(AodSettings(quietHours = true, quietStart = 9, quietEnd = 17).isQuiet(17))
        assertTrue(AodSettings(quietHours = true, quietStart = 0, quietEnd = 0).isQuiet(12))
    }
}
