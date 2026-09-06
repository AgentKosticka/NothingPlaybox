package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.*
import kotlin.math.*
import kotlin.random.Random

object ExpandedEffects {
    val engines by lazy {
        listOf(
            engine("ripple-field", "RIPPLE FIELD", "Interfering waves from drifting sources. Tune speed, wavelength and source count.", ProceduralSpec.RippleField()),
            engine("starfield", "STARFIELD", "Depth-projected stars with adjustable speed, population and trails.", ProceduralSpec.Starfield()),
        )
    }
    private fun engine(id: String, name: String, description: String, spec: ProceduralSpec): PlayboxEffect {
        val effect = PlayboxEffect(id = "builtin-$id", name = name, description = description, builtIn = true, procedural = spec, frames = listOf(EffectFrame()))
        return effect.copy(frames = listOf(ProceduralEffectRuntime(effect).frameAt(0)))
    }
    val animations by lazy {
        listOf(
            animation("radar-sweep", "RADAR SWEEP", "A rotating beam with a fading phosphor trail") { x, y, t ->
                val angle = atan2(y, x)
                val age = ((t * 2 * PI - angle) % (2 * PI) + 2 * PI) % (2 * PI)
                exp(-age * 2.3) * .9 + if (abs(hypot(x, y) - 4.5) < .35) .1 else 0.0
            },
            animation("breathing-orbit", "BREATHING ORBIT", "A soft ring expands and contracts") { x, y, t ->
                exp(-(hypot(x, y) - (3.5 + sin(t * 2 * PI) * 2)).pow(2) / .65)
            },
            animation("woven-light", "WOVEN LIGHT", "Crossing ribbons weave a luminous lattice") { x, y, t ->
                ((sin(x * .8 + t * 2 * PI) * cos(y * .8 - t * 2 * PI) + 1) / 2).pow(3)
            },
        )
    }
    private fun animation(id: String, name: String, description: String, sample: (Double, Double, Double) -> Double) = PlayboxEffect(
        id = "builtin-$id", name = name, description = description, builtIn = true,
        frames = List(48) { frame -> EffectFrame(IntArray(PIXEL_COUNT) { i ->
            (sample((i % MATRIX_SIZE - 6).toDouble(), (i / MATRIX_SIZE - 6).toDouble(), frame / 48.0) * 255).roundToInt().coerceIn(0, 255)
        }, 83).normalized() },
    )
}

internal fun rippleFrame(spec: ProceduralSpec.RippleField, elapsedMs: Long): EffectFrame {
    val t = elapsedMs / 1000.0 * spec.speed
    return EffectFrame(IntArray(PIXEL_COUNT) { i ->
        val x = (i % MATRIX_SIZE - 6).toDouble()
        val y = (i / MATRIX_SIZE - 6).toDouble()
        var field = 0.0
        repeat(spec.sources) { source ->
            val angle = source * 2 * PI / spec.sources + t * .21
            val distance = hypot(x - cos(angle) * 3, y - sin(angle) * 3)
            field += sin(distance * 2 * PI / spec.wavelength - t * 3)
        }
        (((field / spec.sources + 1) / 2).pow(2) * 255).roundToInt()
    }, spec.frameDurationMs).normalized()
}

internal class StarfieldSimulation(private val spec: ProceduralSpec.Starfield) {
    private val random = Random((spec.seed xor (spec.seed ushr 32)).toInt())
    private val stars = List(spec.stars) { Triple(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble()) }
    fun frame(elapsedMs: Long): EffectFrame {
        val time = elapsedMs / 1000.0 * spec.speed * .24
        val pixels = IntArray(PIXEL_COUNT)
        stars.forEach { (x, y, phase) ->
            val z = 1.0 - ((time + phase) % 1.0)
            repeat(1 + (spec.trails * 4).roundToInt()) { trail ->
                val depth = z + trail * .045
                val px = (6 + x * 3 / depth).roundToInt()
                val py = (6 + y * 3 / depth).roundToInt()
                if (px in 0 until MATRIX_SIZE && py in 0 until MATRIX_SIZE) {
                    val index = py * MATRIX_SIZE + px
                    pixels[index] = max(pixels[index], ((1.0 - z * .65) * 255 / (1 + trail * .65)).roundToInt())
                }
            }
        }
        return EffectFrame(pixels, spec.frameDurationMs).normalized()
    }
}
