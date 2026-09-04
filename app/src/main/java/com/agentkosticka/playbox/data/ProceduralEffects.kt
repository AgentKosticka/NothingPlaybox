package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.ProceduralSpec
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

    internal fun lifeStep(current: IntArray): IntArray {
        require(current.size == PIXEL_COUNT)
        val stepped = lifeStep(BooleanArray(PIXEL_COUNT) { current[it] > 0 })
        return IntArray(PIXEL_COUNT) { if (stepped[it]) 255 else 0 }
    }

    internal fun randomLifeSeed(seed: Long, density: Float = 0.28f): IntArray {
        val random = Random(seed.foldToInt())
        val cells = IntArray(PIXEL_COUNT) { index ->
            if (PHONE_4A_PRO_MASK[index] && random.nextFloat() < density.coerceIn(0.05f, 0.75f)) 255 else 0
        }
        // Always include a small oscillator so a sparse random seed cannot start completely dead.
        listOf(5 to 6, 6 to 6, 7 to 6).forEach { (x, y) -> cells[y * MATRIX_SIZE + x] = 255 }
        return cells
    }

    private fun conwayLife(): PlayboxEffect {
        val spec = ProceduralSpec.ConwayLife(
            frameDurationMs = 140,
            initialState = randomLifeSeed(0xC0FFEEL),
        )
        return proceduralEffect(
            id = "builtin-conway-life",
            name = "CONWAY LIFE",
            description = "Live Conway simulation — edit the seed, step it manually, and control evolution speed",
            spec = spec,
        )
    }

    private fun shiftingNoise(): PlayboxEffect = proceduralEffect(
        id = "builtin-shifting-noise",
        name = "SHIFTING NOISE",
        description = "Live layered noise with adjustable drift speed, scale, detail and seed",
        spec = ProceduralSpec.ShiftingNoise(),
    )

    private fun lavaLamp(): PlayboxEffect = proceduralEffect(
        id = "builtin-lava-lamp",
        name = "LAVA LAMP",
        description = "Live metaballs with adjustable speed, blob count, softness and seed",
        spec = ProceduralSpec.LavaLamp(),
    )

    private fun proceduralEffect(
        id: String,
        name: String,
        description: String,
        spec: ProceduralSpec,
    ): PlayboxEffect {
        val shell = PlayboxEffect(
            id = id,
            name = name,
            description = description,
            frames = listOf(EffectFrame(durationMs = spec.frameDurationMs)),
            procedural = spec,
            builtIn = true,
        )
        val preview = ProceduralEffectRuntime(shell).frameAt(0)
        return shell.copy(frames = listOf(preview))
    }

    internal fun sampleNoise(grid: DoubleArray, x: Double, y: Double): Double {
        val gridSize = kotlin.math.sqrt(grid.size.toDouble()).roundToInt()
        require(gridSize * gridSize == grid.size)
        val xFloor = floor(x)
        val yFloor = floor(y)
        val x0 = xFloor.toInt()
        val y0 = yFloor.toInt()
        val tx = x - xFloor
        val ty = y - yFloor
        val sx = tx * tx * (3.0 - 2.0 * tx)
        val sy = ty * ty * (3.0 - 2.0 * ty)

        fun value(ix: Int, iy: Int): Double {
            val wrappedX = Math.floorMod(ix, gridSize)
            val wrappedY = Math.floorMod(iy, gridSize)
            return grid[wrappedY * gridSize + wrappedX]
        }
        fun lerp(a: Double, b: Double, amount: Double) = a + (b - a) * amount

        val top = lerp(value(x0, y0), value(x0 + 1, y0), sx)
        val bottom = lerp(value(x0, y0 + 1), value(x0 + 1, y0 + 1), sx)
        return lerp(top, bottom, sy)
    }
}

class ProceduralEffectRuntime(private val effect: PlayboxEffect) {
    private val spec = requireNotNull(effect.procedural) { "Effect is not procedural" }
    private var lifeState = (spec as? ProceduralSpec.ConwayLife)?.initialState?.copyOf()
    private var lifeGeneration = 0L
    private val afterglow = IntArray(PIXEL_COUNT)

    private val noiseGrids: Pair<DoubleArray, DoubleArray>? = (spec as? ProceduralSpec.ShiftingNoise)?.let { noise ->
        val random = Random(noise.seed.foldToInt())
        DoubleArray(NOISE_GRID * NOISE_GRID) { random.nextDouble() } to
            DoubleArray(NOISE_GRID * NOISE_GRID) { random.nextDouble() }
    }

    private val lavaBlobs: List<LavaBlob>? = (spec as? ProceduralSpec.LavaLamp)?.let { lava ->
        val random = Random(lava.seed.foldToInt())
        List(lava.blobCount) { index ->
            LavaBlob(
                xRadius = 0.45 + random.nextDouble() * 0.8,
                yRadius = 0.45 + random.nextDouble() * 0.8,
                phase = random.nextDouble() * TAU,
                strength = 0.24 + random.nextDouble() * 0.14,
                speed = 0.65 + (index % 3) * 0.27 + random.nextDouble() * 0.18,
            )
        }
    }

    fun frameAt(elapsedMs: Long): EffectFrame = when (val current = spec) {
        is ProceduralSpec.ConwayLife -> renderLife(current, elapsedMs.coerceAtLeast(0))
        is ProceduralSpec.ShiftingNoise -> renderNoise(current, elapsedMs.coerceAtLeast(0))
        is ProceduralSpec.LavaLamp -> renderLava(current, elapsedMs.coerceAtLeast(0))
    }

    private fun renderLife(spec: ProceduralSpec.ConwayLife, elapsedMs: Long): EffectFrame {
        val targetGeneration = elapsedMs / spec.frameDurationMs
        if (targetGeneration < lifeGeneration) {
            lifeState = spec.initialState.copyOf()
            lifeGeneration = 0
            afterglow.fill(0)
        }
        while (lifeGeneration < targetGeneration) {
            val current = requireNotNull(lifeState)
            val next = ProceduralEffects.lifeStep(current)
            for (index in 0 until PIXEL_COUNT) {
                afterglow[index] = when {
                    !PHONE_4A_PRO_MASK[index] -> 0
                    current[index] > 0 && next[index] == 0 -> 112
                    else -> (afterglow[index] * 0.48).roundToInt()
                }
            }
            lifeState = next
            lifeGeneration++
        }
        val state = requireNotNull(lifeState)
        return EffectFrame(
            pixels = IntArray(PIXEL_COUNT) { index ->
                if (state[index] > 0) 255 else afterglow[index]
            },
            durationMs = spec.frameDurationMs,
        ).normalized()
    }

    private fun renderNoise(spec: ProceduralSpec.ShiftingNoise, elapsedMs: Long): EffectFrame {
        val (coarse, fine) = requireNotNull(noiseGrids)
        val time = elapsedMs / 1_000.0 * spec.speed
        val ax = cos(time * 0.72) * 1.55
        val ay = sin(time * 0.61) * 1.55
        val bx = cos(-time * 0.93 + 1.7) * 2.1
        val by = sin(-time * 0.81 + 1.7) * 2.1
        val broadScale = 0.33 * spec.scale
        val fineScale = 0.57 * spec.scale
        val detail = spec.detail.toDouble()
        return EffectFrame(IntArray(PIXEL_COUNT) { index ->
            if (!PHONE_4A_PRO_MASK[index]) return@IntArray 0
            val x = (index % MATRIX_SIZE).toDouble()
            val y = (index / MATRIX_SIZE).toDouble()
            val broad = ProceduralEffects.sampleNoise(coarse, x * broadScale + ax, y * broadScale + ay)
            val fineValue = ProceduralEffects.sampleNoise(fine, x * fineScale + bx, y * fineScale + by)
            val field = broad * (1.0 - detail) + fineValue * detail
            val normalized = ((field - 0.16) / 0.7).coerceIn(0.0, 1.0)
            val smooth = normalized * normalized * (3.0 - 2.0 * normalized)
            (smooth * 255.0).roundToInt()
        }, spec.frameDurationMs).normalized()
    }

    private fun renderLava(spec: ProceduralSpec.LavaLamp, elapsedMs: Long): EffectFrame {
        val blobs = requireNotNull(lavaBlobs)
        val time = elapsedMs / 1_000.0 * spec.speed
        return EffectFrame(IntArray(PIXEL_COUNT) { index ->
            if (!PHONE_4A_PRO_MASK[index]) return@IntArray 0
            val x = (index % MATRIX_SIZE - 6) / 6.0
            val y = (index / MATRIX_SIZE - 6) / 6.0
            var field = 0.0
            blobs.forEachIndexed { blobIndex, blob ->
                val angle = time * blob.speed + blob.phase
                val cx = sin(angle) * blob.xRadius + sin(angle * 1.7 + blobIndex) * 0.08
                val cy = cos(angle * 0.83 + blob.phase) * blob.yRadius + sin(angle * 1.31 + blob.phase) * 0.1
                val dx = x - cx
                val dy = y - cy
                val distanceSquared = dx * dx + dy * dy
                field += blob.strength / (distanceSquared + spec.softness)
            }
            val threshold = 1.25 + blobs.size * 0.08
            val shaped = ((field - threshold) / 3.8).coerceIn(0.0, 1.0).pow(1.18)
            (shaped * 255.0).roundToInt()
        }, spec.frameDurationMs).normalized()
    }

    private data class LavaBlob(
        val xRadius: Double,
        val yRadius: Double,
        val phase: Double,
        val strength: Double,
        val speed: Double,
    )

    private companion object {
        const val NOISE_GRID = 8
        const val TAU = 6.283185307179586
    }
}

private fun Long.foldToInt(): Int = (this xor (this ushr 32)).toInt()
