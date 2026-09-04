package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import kotlin.math.roundToInt

/** Organic procedural effects that evolve a tiny simulation into matrix-ready frames. */
object OrganicEffects {
    val all: List<PlayboxEffect> by lazy {
        listOf(reactionDiffusion())
    }

    private fun reactionDiffusion(): PlayboxEffect {
        var a = DoubleArray(PIXEL_COUNT) { 1.0 }
        var b = DoubleArray(PIXEL_COUNT)

        // Seed a few asymmetric islands so the pattern grows instead of settling into a boring bullseye.
        listOf(5 to 5, 6 to 5, 7 to 5, 5 to 6, 6 to 6, 8 to 7, 7 to 8).forEach { (x, y) ->
            val index = y * MATRIX_SIZE + x
            if (PHONE_4A_PRO_MASK[index]) b[index] = 1.0
        }

        val frames = buildList {
            repeat(FRAME_COUNT) {
                repeat(STEPS_PER_FRAME) {
                    val nextA = a.copyOf()
                    val nextB = b.copyOf()
                    for (index in 0 until PIXEL_COUNT) {
                        if (!PHONE_4A_PRO_MASK[index]) continue
                        val lapA = laplacian(a, index)
                        val lapB = laplacian(b, index)
                        val reaction = a[index] * b[index] * b[index]
                        nextA[index] = (a[index] + DIFFUSION_A * lapA - reaction + FEED * (1.0 - a[index]))
                            .coerceIn(0.0, 1.0)
                        nextB[index] = (b[index] + DIFFUSION_B * lapB + reaction - (KILL + FEED) * b[index])
                            .coerceIn(0.0, 1.0)
                    }
                    a = nextA
                    b = nextB
                }

                add(EffectFrame(IntArray(PIXEL_COUNT) { index ->
                    if (!PHONE_4A_PRO_MASK[index]) return@IntArray 0
                    val edge = ((b[index] - a[index] * 0.34) * 420.0).coerceIn(0.0, 255.0)
                    edge.roundToInt()
                }, FRAME_DURATION_MS).normalized())
            }
        }

        return PlayboxEffect(
            id = "builtin-reaction-diffusion",
            name = "ORGANIC BLOOM",
            description = "Reaction-diffusion cells curl and split into living monochrome patterns",
            frames = frames,
            builtIn = true,
        )
    }

    private fun laplacian(values: DoubleArray, index: Int): Double {
        val x = index % MATRIX_SIZE
        val y = index / MATRIX_SIZE
        var result = -values[index]
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until MATRIX_SIZE || ny !in 0 until MATRIX_SIZE) continue
            val neighbor = ny * MATRIX_SIZE + nx
            if (!PHONE_4A_PRO_MASK[neighbor]) continue
            val weight = if (dx == 0 || dy == 0) 0.2 else 0.05
            result += values[neighbor] * weight
        }
        return result
    }

    private const val FRAME_COUNT = 56
    private const val STEPS_PER_FRAME = 3
    private const val FRAME_DURATION_MS = 75
    private const val DIFFUSION_A = 1.0
    private const val DIFFUSION_B = 0.5
    private const val FEED = 0.055
    private const val KILL = 0.062
}
