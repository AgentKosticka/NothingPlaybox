package com.agentkosticka.playbox.matrix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphMatrixClientTest {
    @Test
    fun phone4aProModelIsAcceptedForNothingHardware() {
        assertTrue(isPhone4aPro(manufacturer = "Nothing", brand = "Nothing", model = "A069P"))
        assertTrue(isPhone4aPro(manufacturer = "NOTHING", brand = "generic", model = "a069p"))
    }

    @Test
    fun otherNothingPhonesAndSpoofedModelsAreRejected() {
        assertFalse(isPhone4aPro(manufacturer = "Nothing", brand = "Nothing", model = "A059P"))
        assertFalse(isPhone4aPro(manufacturer = "Google", brand = "Pixel", model = "A069P"))
    }
}
