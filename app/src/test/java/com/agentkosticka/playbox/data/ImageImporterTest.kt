package com.agentkosticka.playbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
