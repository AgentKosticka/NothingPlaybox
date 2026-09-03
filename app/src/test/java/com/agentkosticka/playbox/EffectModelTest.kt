package com.agentkosticka.playbox

import com.agentkosticka.playbox.data.EffectCatalog
import com.agentkosticka.playbox.matrix.HardwareFrameEncoder
import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.LoopMode
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.frameIndexAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectModelTest {
    @Test
    fun phone4aProMaskContains137Pixels() {
        assertEquals(169, PHONE_4A_PRO_MASK.size)
        assertEquals(137, PHONE_4A_PRO_MASK.count { it })
        val rowWidths = (0 until 13).map { row ->
            (0 until 13).count { column -> PHONE_4A_PRO_MASK[row * 13 + column] }
        }
        assertEquals(listOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5), rowWidths)
    }

    @Test
    fun normalizationClampsAndMasksPixels() {
        val normalized = EffectFrame(IntArray(PIXEL_COUNT) { if (it % 2 == 0) -20 else 999 }).normalized()
        normalized.pixels.forEachIndexed { index, value ->
            if (!PHONE_4A_PRO_MASK[index]) assertEquals(0, value)
            else assertTrue(value == 0 || value == 255)
        }
    }

    @Test
    fun loopTimingUsesFrameDurations() {
        val frames = listOf(EffectFrame(durationMs = 100), EffectFrame(durationMs = 200), EffectFrame(durationMs = 300))
        val loop = PlayboxEffect(name = "test", frames = frames)
        assertEquals(0, loop.frameIndexAt(0))
        assertEquals(1, loop.frameIndexAt(100))
        assertEquals(2, loop.frameIndexAt(300))
        assertEquals(0, loop.frameIndexAt(600))

        val hold = loop.copy(loopMode = LoopMode.HOLD)
        assertEquals(2, hold.frameIndexAt(50_000))
    }

    @Test
    fun allBuiltInsAreHardwareSafe() {
        assertEquals(18, EffectCatalog.builtIns.size)
        assertTrue(EffectCatalog.builtIns.any { it.name.contains("EYE") })
        assertTrue(EffectCatalog.builtIns.any { it.name.contains("BEER") })
        assertTrue(EffectCatalog.builtIns.any { it.name.contains("BLACK HOLE") })
        assertTrue(EffectCatalog.builtIns.any { it.name.contains("SKULL") })
        assertTrue(EffectCatalog.builtIns.any { it.name.contains("LIFE") })
        assertTrue(EffectCatalog.builtIns.any { it.name.contains("NOISE") })
        EffectCatalog.builtIns.forEach { effect ->
            assertTrue(effect.frames.isNotEmpty())
            effect.frames.forEach { frame ->
                assertEquals(PIXEL_COUNT, frame.pixels.size)
                frame.pixels.forEachIndexed { index, value ->
                    assertTrue(value in 0..255)
                    if (!PHONE_4A_PRO_MASK[index]) assertEquals(0, value)
                }
            }
        }
    }

    @Test
    fun editorBrightnessUsesFullElevenBitHardwareRange() {
        val encoded = HardwareFrameEncoder.encode(intArrayOf(-1, 0, 1, 64, 128, 255, 999))
        assertEquals(0, encoded[0])
        assertEquals(0, encoded[1])
        assertEquals(8, encoded[2])
        assertEquals(514, encoded[3])
        assertEquals(1028, encoded[4])
        assertEquals(2047, encoded[5])
        assertEquals(2047, encoded[6])
    }
}
