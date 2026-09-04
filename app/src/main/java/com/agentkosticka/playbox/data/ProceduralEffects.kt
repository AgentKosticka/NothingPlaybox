package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

object ProceduralEffects {
    val all: List<PlayboxEffect> by lazy {
        listOf(conwayLife(), shiftingNoise(), lavaLamp())
    }

    internal fun lifeStep(current: BooleanArray): BooleanArray {
        require(current.size == PIXEL_COUNT)
        return BooleanArray(PIXEL_COUNT) { index ->
            if (!PHONE_4A_PRO_MASK[index]) return@BooleanArray false
            val x = index % MATRIX_SIZE
            val y = index / MATRIX_SIZE
            var neighbors = 0
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until MATRIX_SIZE || ny !in 0 until MATRIX_SIZE) continue
                val neighbor = ny * MATRIX_SIZE + nx
                if (PHONE_4A_PRO_MASK[neighbor] && current[neighbor]) neighbors++
            }
            neighbors == 3 || (current[index] && neighbors == 2)
        }
    }

    private fun conwayLife(): PlayboxEffect {
        val random = Random(0xC0FFEE)
        var state = seedLife(random)
        var previousState: BooleanArray? = null
        val afterglow = IntArray(PIXEL_COUNT)
        val frames = buildList {
            repeat(56) {
                add(EffectFrame(IntArray(PIXEL_COUNT) { index ->
                    when {
                        !PHONE_4A_PRO_MASK[index] -> 0
                        state[index] -> 255
                        else -> afterglow[index]
                    }
                }, 90).normalized())

                val next = lifeStep(state)
                for (index in 0 until PIXEL_COUNT) {
                    afterglow[index] = when {
                        !PHONE_4A_PRO_MASK[index] -> 0
                        state[index] && !next[index] -> 125
                        else -> (afterglow[index] * 0.52).roundToInt()
                    }
                }

                val population = next.count { it }
                val stalled = next.contentEquals(state) || previousState?.let { next.contentEquals(it) } == true
                if (population < 4 || stalled) {
                    // Let the old generation fade under the new seed instead of hard-cutting it away.
                    for (index in 0 until PIXEL_COUNT) {
                        if (state[index]) afterglow[index] = maxOf(afterglow[index], 105)
                    }
                    state = seedLife(random)
                    previousState = null
                } else {
                    previousState = state
                    state = next
                }
            }
        }
        return PlayboxEffect(
            id = "builtin-conway-life",
            name = "CONWAY LIFE",
            description = "Cells bloom, collide and reseed with a fading afterglow",
            frames = frames,
            builtIn = true,
        )
    }

    private fun seedLife(random: Random): BooleanArray {
        val state = BooleanArray(PIXEL_COUNT) { index ->
            PHONE_4A_PRO_MASK[index] && random.nextDouble() < 0.28
        }
        // Give every generation a lively center instead of relying on a lucky random seed.
        listOf(5 to 6, 6 to 6, 7 to 6, 7 to 5, 6 to 4).forEach { (x, y) ->
            state[y * MATRIX_SIZE + x] = true
        }
        return state
    }

    private fun shiftingNoise(): PlayboxEffect {
        val random = Random(0x51F7)
        val coarse = DoubleArray(NOISE_GRID * NOISE_GRID) { random.nextDouble() }
        val fine = DoubleArray(NOISE_GRID * NOISE_GRID) { random.nextDouble() }
        val frames = (0 until 60).map { tick ->
            val phase = tick * PI * 2.0 / 60.0
            val ax = cos(phase) * 1.55
            val ay = sin(phase) * 1.55
            val bx = cos(-phase + 1.7) * 2.1
            val by = sin(-phase + 1.7) * 2.1
            EffectFrame(IntArray(PIXEL_COUNT) { index ->
                if (!PHONE_4A_PRO_MASK[index]) return@IntArray 0
                val x = (index % MATRIX_SIZE).toDouble()
                val y = (index / MATRIX_SIZE).toDouble()
                val broad = sampleNoise(coarse, x * 0.33 + ax, y * 0.33 + ay)
                val detail = sampleNoise(fine, x * 0.57 + bx, y * 0.57 + by)
                val field = broad * 0.72 + detail * 0.28
                val normalized = ((field - 0.18) / 0.68).coerceIn(0.0, 1.0)
                val smooth = normalized * normalized * (3.0 - 2.0 * normalized)
                (smooth * 255.0).roundToInt()
            }, 70).normalized()
        }
        return PlayboxEffect(
            id = "builtin-shifting-noise",
            name = "SHIFTING NOISE",
            description = "Seamless drifting value-noise clouds with layered motion",
            frames = frames,
            builtIn = true,
        )
    }

    private fun lavaLamp(): PlayboxEffect {
        val blobs = listOf(
            LavaBlob(0.8, 0.92, 0.2, 0.18, 0.35, 1),
            LavaBlob(1.25, 0.7, 1.9, 0.14, 0.31, 2),
            LavaBlob(0.55, 1.15, 3.4, 0.2, 0.29, 1),
            LavaBlob(1.05, 0.58, 5.1, 0.16, 0.27, 2),
        )
        val frames = (0 until 72).map { tick ->
            val phase = tick * PI * 2.0 / 72.0
            EffectFrame(IntArray(PIXEL_COUNT) { index ->
                if (!PHONE_4A_PRO_MASK[index]) return@IntArray 0
                val x = (index % MATRIX_SIZE - 6) / 6.0
                val y = (index / MATRIX_SIZE - 6) / 6.0
                var field = 0.0
                blobs.forEachIndexed { blobIndex, blob ->
                    val angle = phase * blob.speed + blob.phase
                    val cx = sin(angle) * blob.xRadius + sin(angle * 2.0 + blobIndex) * 0.08
                    val cy = cos(phase * (blob.speed + 1) + blob.phase) * blob.yRadius + sin(phase * 3.0 + blob.phase) * 0.1
                    val dx = x - cx
                    val dy = y - cy
                    val distanceSquared = dx * dx + dy * dy
                    field += blob.strength / (distanceSquared + blob.softness)
                }
                val shaped = ((field - 1.55) / 4.1).coerceIn(0.0, 1.0).pow(1.25)
                (shaped * 255.0).roundToInt()
            }, 75).normalized()
        }
        return PlayboxEffect(
            id = "builtin-lava-lamp",
            name = "LAVA LAMP",
            description = "Soft metaballs merge and drift in a seamless liquid loop",
            frames = frames,
            builtIn = true,
        )
    }

    private data class LavaBlob(
        val xRadius: Double,
        val yRadius: Double,
        val phase: Double,
        val softness: Double,
        val strength: Double,
        val speed: Int,
    )

    private fun sampleNoise(grid: DoubleArray, x: Double, y: Double): Double {
        val xFloor = floor(x)
        val yFloor = floor(y)
        val x0 = xFloor.toInt()
        val y0 = yFloor.toInt()
        val tx = x - xFloor
        val ty = y - yFloor
        val sx = tx * tx * (3.0 - 2.0 * tx)
        val sy = ty * ty * (3.0 - 2.0 * ty)

        fun value(ix: Int, iy: Int): Double {
            val wrappedX = Math.floorMod(ix, NOISE_GRID)
            val wrappedY = Math.floorMod(iy, NOISE_GRID)
            return grid[wrappedY * NOISE_GRID + wrappedX]
        }
        fun lerp(a: Double, b: Double, amount: Double) = a + (b - a) * amount

        val top = lerp(value(x0, y0), value(x0 + 1, y0), sx)
        val bottom = lerp(value(x0, y0 + 1), value(x0 + 1, y0 + 1), sx)
        return lerp(top, bottom, sy)
    }

    private const val NOISE_GRID = 8
}
