package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PIXEL_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralEffectsTest {
    @Test
    fun conwayBlinkerOscillatesByStandardRules() {
        val horizontal = BooleanArray(PIXEL_COUNT).apply {
            this[6 * MATRIX_SIZE + 5] = true
            this[6 * MATRIX_SIZE + 6] = true
            this[6 * MATRIX_SIZE + 7] = true
        }

        val vertical = ProceduralEffects.lifeStep(horizontal)

        assertTrue(vertical[5 * MATRIX_SIZE + 6])
        assertTrue(vertical[6 * MATRIX_SIZE + 6])
        assertTrue(vertical[7 * MATRIX_SIZE + 6])
        assertEquals(3, vertical.count { it })
        assertTrue(ProceduralEffects.lifeStep(vertical).contentEquals(horizontal))
    }

    @Test
    fun proceduralEffectsContainRealMotion() {
        val life = ProceduralEffects.all.single { it.name == "CONWAY LIFE" }
        val noise = ProceduralEffects.all.single { it.name == "SHIFTING NOISE" }

        assertEquals(56, life.frames.size)
        assertEquals(60, noise.frames.size)
        assertTrue(life.frames.zipWithNext().any { (a, b) -> !a.pixels.contentEquals(b.pixels) })
        assertTrue(noise.frames.zipWithNext().all { (a, b) -> !a.pixels.contentEquals(b.pixels) })
        assertFalse(noise.frames.first().pixels.contentEquals(noise.frames.last().pixels))
    }
}
