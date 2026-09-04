package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.ProceduralSpec
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
    fun proceduralBuiltInsStoreSettingsNotBakedAnimations() {
        val procedural = ProceduralEffects.all + OrganicEffects.all

        assertEquals(4, procedural.size)
        procedural.forEach { effect ->
            assertTrue(effect.procedural != null)
            assertEquals("Procedural effects should only keep one static compatibility thumbnail", 1, effect.frames.size)
            assertTrue(effect.isAnimated)
        }
    }

    @Test
    fun liveProceduralFieldsChangeWithoutStoredFrameSequences() {
        val effects = ProceduralEffects.all + OrganicEffects.all
        listOf("SHIFTING NOISE", "LAVA LAMP", "ORGANIC BLOOM").forEach { name ->
            val effect = effects.single { it.name == name }
            val runtime = ProceduralEffectRuntime(effect)
            val first = runtime.frameAt(0).pixels
            val later = runtime.frameAt(750).pixels

            assertFalse("$name should be generated live", first.contentEquals(later))
            assertEquals(1, effect.frames.size)
        }
    }

    @Test
    fun organicBloomIsDeterministicForTheSameSeedAndSettings() {
        val template = OrganicEffects.all.single()
        val first = ProceduralEffectRuntime(template).frameAt(900).pixels
        val second = ProceduralEffectRuntime(template).frameAt(900).pixels

        assertTrue(first.contentEquals(second))
    }

    @Test
    fun conwayRuntimeHonorsCustomSeedAndStepTimeWithoutAfterglow() {
        val horizontal = IntArray(PIXEL_COUNT).apply {
            this[6 * MATRIX_SIZE + 5] = 255
            this[6 * MATRIX_SIZE + 6] = 255
            this[6 * MATRIX_SIZE + 7] = 255
        }
        val template = ProceduralEffects.all.single { it.name == "CONWAY LIFE" }
        val effect = template.copy(
            procedural = ProceduralSpec.ConwayLife(frameDurationMs = 200, initialState = horizontal),
        )
        val runtime = ProceduralEffectRuntime(effect)

        assertTrue(runtime.frameAt(0).pixels.contentEquals(horizontal))
        assertTrue(runtime.frameAt(199).pixels.contentEquals(horizontal))
        val vertical = runtime.frameAt(200).pixels
        assertEquals(255, vertical[5 * MATRIX_SIZE + 6])
        assertEquals(255, vertical[6 * MATRIX_SIZE + 6])
        assertEquals(255, vertical[7 * MATRIX_SIZE + 6])
        assertTrue("Conway frames should contain only dead/off or alive/full-brightness cells", vertical.all { it == 0 || it == 255 })
    }
}
