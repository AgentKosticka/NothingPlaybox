package com.agentkosticka.playbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixDisplayTest {
    @Test
    fun previewLuminanceIsMonotonicAcrossEveryEditorIntensity() {
        var previous = previewLuminance(0)
        assertTrue(previous > 0f)
        for (intensity in 1..255) {
            val current = previewLuminance(intensity)
            assertTrue("$intensity must not render darker than ${intensity - 1}", current >= previous)
            previous = current
        }
        assertEquals(1f, previewLuminance(255), 0.0001f)
    }

    @Test
    fun previewLuminanceClampsOutOfRangeValues() {
        assertEquals(previewLuminance(0), previewLuminance(-500), 0f)
        assertEquals(previewLuminance(255), previewLuminance(500), 0f)
    }
}
