package com.agentkosticka.playbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageImporterTest {
    @Test
    fun decodeSizeLeavesSmallImagesAlone() {
        assertNull(boundedImageDecodeSize(800, 600))
    }

    @Test
    fun decodeSizePreservesAspectRatioForLargeImages() {
        assertEquals(
            ImageDecodeSize(width = 1_024, height = 512),
            boundedImageDecodeSize(4_000, 2_000),
        )
        assertEquals(
            ImageDecodeSize(width = 512, height = 1_024),
            boundedImageDecodeSize(2_000, 4_000),
        )
    }

    @Test
    fun decodeSizeNeverProducesZeroDimension() {
        assertEquals(
            ImageDecodeSize(width = 1_024, height = 1),
            boundedImageDecodeSize(100_000, 1),
        )
    }

    @Test
    fun transparentPixelsStayOffRegardlessOfRgb() {
        assertEquals(0, colorToMatrixIntensity(0x00FFFFFF))
        assertEquals(0, colorToMatrixIntensity(0x00FF0000))
    }

    @Test
    fun alphaScalesVisibleLuminanceOverBlack() {
        val transparent = colorToMatrixIntensity(0x00FFFFFF)
        val half = colorToMatrixIntensity(0x80FFFFFF.toInt())
        val opaque = colorToMatrixIntensity(0xFFFFFFFF.toInt())

        assertEquals(0, transparent)
        assertEquals(255, opaque)
        assertTrue(half in 1 until opaque)
    }
}
