package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.*
import kotlin.math.*
import kotlin.random.Random

/** Reaction-diffusion at 3× display resolution with no-flux edges and recurring inoculation. */
internal class BloomSimulation(private val spec: ProceduralSpec.OrganicBloom) {
    private val size = MATRIX_SIZE * 3
    private var a = DoubleArray(size * size) { 1.0 }
    private var b = DoubleArray(size * size)
    private var nextA = DoubleArray(size * size)
    private var nextB = DoubleArray(size * size)
    private var frame = 0L

    init { reset() }

    private fun reset() {
        a.fill(1.0); b.fill(0.0); frame = 0
        repeat(5) { plant(it.toLong()) }
        repeat(100) { step() }
    }

    private fun plant(sequence: Long) {
        val random = Random((spec.seed xor (sequence * 104729L)).toInt())
        val cx = 6 + random.nextInt(size - 12)
        val cy = 6 + random.nextInt(size - 12)
        for (dy in -2..2) for (dx in -2..2) {
            val i = (cy + dy) * size + cx + dx
            a[i] = .5; b[i] = .8
        }
    }

    fun frameAt(elapsedMs: Long): EffectFrame {
        val target = elapsedMs.coerceAtLeast(0) / spec.frameDurationMs
        if (target < frame) reset()
        // A device waking after a long pause must not replay hours of simulation on one frame.
        if (target - frame > 180) { reset(); frame = target - 180 }
        while (frame < target) {
            repeat(8) { step() }
            frame++
            if (frame % 36L == 0L) plant(frame / 36 + 5)
        }
        return EffectFrame(IntArray(PIXEL_COUNT) { i ->
            var concentration = 0.0
            for (dy in 0..2) for (dx in 0..2) {
                concentration += b[(i / MATRIX_SIZE * 3 + dy) * size + i % MATRIX_SIZE * 3 + dx]
            }
            ((concentration / 9 - .02) * 780).roundToInt().coerceIn(0, 255)
        }, spec.frameDurationMs).normalized()
    }

    private fun step() {
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            var lapA = -a[i]; var lapB = -b[i]
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val j = (y + dy).coerceIn(0, size - 1) * size + (x + dx).coerceIn(0, size - 1)
                val weight = if (dx == 0 || dy == 0) .2 else .05
                lapA += a[j] * weight; lapB += b[j] * weight
            }
            val reaction = a[i] * b[i] * b[i]
            nextA[i] = (a[i] + lapA - reaction + spec.feed * (1 - a[i])).coerceIn(0.0, 1.0)
            nextB[i] = (b[i] + .5 * lapB + reaction - (spec.kill + spec.feed) * b[i]).coerceIn(0.0, 1.0)
        }
        val oldA = a; a = nextA; nextA = oldA
        val oldB = b; b = nextB; nextB = oldB
    }
}
