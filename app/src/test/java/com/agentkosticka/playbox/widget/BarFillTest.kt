package com.agentkosticka.playbox.widget

import org.junit.Assert.*
import org.junit.Test

class BarFillTest {
    @Test fun directionsMirrorAndDotsFillIndividually() {
        val left = filledDots(64, 3.0 / 64, BarFill.LEFT_TO_RIGHT, 0)
        val right = filledDots(64, 3.0 / 64, BarFill.RIGHT_TO_LEFT, 0)
        assertEquals(listOf(0, 1, 2), left.indices.filter { left[it] })
        assertEquals(listOf(61, 62, 63), right.indices.filter { right[it] })
        assertArrayEquals(left.reversedArray(), right)
    }

    @Test fun densityIsStableAndOnlyAddsDots() {
        val first = filledDots(64, .25, BarFill.DENSITY, 0)
        val later = filledDots(64, .75, BarFill.DENSITY, 0)
        assertArrayEquals(first, filledDots(64, .25, BarFill.DENSITY, 0))
        assertEquals(16, first.count { it })
        assertEquals(48, later.count { it })
        first.indices.filter { first[it] }.forEach { assertTrue(later[it]) }
        assertFalse(first.contentEquals(filledDots(64, .25, BarFill.LEFT_TO_RIGHT, 0)))
        assertFalse(first.contentEquals(filledDots(64, .25, BarFill.DENSITY, 1)))
    }

    @Test fun everyStyleHandlesEmptyAndFullPeriods() {
        BarFill.entries.forEach { fill ->
            assertEquals(0, filledDots(64, 0.0, fill, 0).count { it })
            assertEquals(64, filledDots(64, 1.0, fill, 0).count { it })
        }
    }
}
