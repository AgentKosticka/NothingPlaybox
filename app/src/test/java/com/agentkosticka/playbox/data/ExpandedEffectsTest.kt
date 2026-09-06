package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExpandedEffectsTest {
    @Test fun bloomRemainsVisibleAndKeepsChangingForAMinute() {
        val runtime = ProceduralEffectRuntime(OrganicEffects.all.single())
        var previous = runtime.frameAt(0).pixels
        var changingSamples = 0
        for (ms in 750L..60_000L step 750) {
            val pixels = runtime.frameAt(ms).pixels
            assertTrue("Bloom went dark at $ms", pixels.count { it > 30 } >= 5)
            if (pixels.indices.sumOf { abs(pixels[it] - previous[it]) } > 250) changingSamples++
            previous = pixels
        }
        assertTrue("Bloom should keep visibly evolving ($changingSamples/80 samples)", changingSamples >= 60)
    }

    @Test fun newEnginesAreDeterministicAndRespondToProfiles() {
        ExpandedEffects.engines.forEach { effect ->
            val first = ProceduralEffectRuntime(effect).frameAt(0).pixels
            val later = ProceduralEffectRuntime(effect).frameAt(1500).pixels
            assertFalse(first.contentEquals(later))
            assertArrayEquals(later, ProceduralEffectRuntime(effect).frameAt(1500).pixels)
            val changed = effect.editableCopy().copy(procedural = when (val spec = effect.procedural) {
                is ProceduralSpec.RippleField -> spec.copy(wavelength = 5f, sources = 1)
                is ProceduralSpec.Starfield -> spec.copy(stars = 60, trails = 1f)
                else -> error("Unexpected engine")
            })
            assertFalse(later.contentEquals(ProceduralEffectRuntime(changed).frameAt(1500).pixels))
            assertTrue(effect.builtIn)
            assertFalse(changed.builtIn)
        }
    }

    @Test fun oneBuiltInProfilePerEngine() {
        val engines = EffectCatalog.builtIns.filter { it.procedural != null }
        assertEquals(engines.size, engines.map { it.procedural!!::class }.distinct().size)
    }
}
