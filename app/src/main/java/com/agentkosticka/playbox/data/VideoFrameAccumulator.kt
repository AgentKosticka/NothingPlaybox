package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.EffectFrame

/**
 * Builds compact animation frames from fixed-rate video samples while preserving
 * time even when the platform decoder misses an individual sample.
 */
internal class VideoFrameAccumulator(
    private val sampleDurationMs: Int,
    private val maxFrameDurationMs: Int = 5_000,
) {
    init {
        require(sampleDurationMs in 33..5_000)
        require(maxFrameDurationMs in sampleDurationMs..5_000)
    }

    private val mutableFrames = mutableListOf<EffectFrame>()
    private var leadingMissingDurationMs = 0

    val frames: List<EffectFrame>
        get() = mutableFrames.toList()

    fun addDecoded(pixels: IntArray) {
        var remaining = sampleDurationMs + leadingMissingDurationMs
        leadingMissingDurationMs = 0
        while (remaining > 0) {
            val previous = mutableFrames.lastOrNull()
            if (previous != null && previous.pixels.contentEquals(pixels) && previous.durationMs < maxFrameDurationMs) {
                val added = minOf(remaining, maxFrameDurationMs - previous.durationMs)
                mutableFrames[mutableFrames.lastIndex] = previous.copy(durationMs = previous.durationMs + added)
                remaining -= added
            } else {
                val duration = minOf(remaining, maxFrameDurationMs)
                mutableFrames += EffectFrame(pixels.copyOf(), duration)
                remaining -= duration
            }
        }
    }

    fun addMissing() {
        if (mutableFrames.isEmpty()) {
            leadingMissingDurationMs += sampleDurationMs
            return
        }

        var remaining = sampleDurationMs
        while (remaining > 0) {
            val previous = mutableFrames.last()
            val available = maxFrameDurationMs - previous.durationMs
            if (available > 0) {
                val added = minOf(remaining, available)
                mutableFrames[mutableFrames.lastIndex] = previous.copy(durationMs = previous.durationMs + added)
                remaining -= added
            } else {
                val duration = minOf(remaining, maxFrameDurationMs)
                mutableFrames += previous.copy(pixels = previous.pixels.copyOf(), durationMs = duration)
                remaining -= duration
            }
        }
    }
}
