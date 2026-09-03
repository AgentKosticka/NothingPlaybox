package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.PIXEL_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameAccumulatorTest {
    @Test
    fun identicalSamplesAreDeduplicatedWithoutLosingTime() {
        val accumulator = VideoFrameAccumulator(sampleDurationMs = 100)
        val pixels = IntArray(PIXEL_COUNT) { 42 }

        repeat(4) { accumulator.addDecoded(pixels) }

        assertEquals(1, accumulator.frames.size)
        assertEquals(400, accumulator.frames.single().durationMs)
        assertTrue(accumulator.frames.single().pixels.contentEquals(pixels))
    }

    @Test
    fun missingSamplesExtendTheLastGoodFrame() {
        val accumulator = VideoFrameAccumulator(sampleDurationMs = 100)
        accumulator.addDecoded(IntArray(PIXEL_COUNT) { 10 })
        accumulator.addMissing()
        accumulator.addMissing()
        accumulator.addDecoded(IntArray(PIXEL_COUNT) { 20 })

        assertEquals(listOf(300, 100), accumulator.frames.map { it.durationMs })
        assertEquals(400, accumulator.frames.sumOf { it.durationMs })
    }

    @Test
    fun leadingDecodeGapsArePreservedWhenFirstFrameArrives() {
        val accumulator = VideoFrameAccumulator(sampleDurationMs = 100)
        accumulator.addMissing()
        accumulator.addMissing()
        accumulator.addDecoded(IntArray(PIXEL_COUNT) { 80 })

        assertEquals(1, accumulator.frames.size)
        assertEquals(300, accumulator.frames.single().durationMs)
    }

    @Test
    fun longStaticSectionsSplitAtModelDurationLimit() {
        val accumulator = VideoFrameAccumulator(sampleDurationMs = 100)
        val pixels = IntArray(PIXEL_COUNT) { 120 }

        repeat(52) { accumulator.addDecoded(pixels) }

        assertEquals(listOf(5_000, 200), accumulator.frames.map { it.durationMs })
        assertEquals(5_200, accumulator.frames.sumOf { it.durationMs })
    }
}
