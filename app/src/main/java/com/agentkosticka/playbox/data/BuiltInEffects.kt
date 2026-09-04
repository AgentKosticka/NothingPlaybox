package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

object BuiltInEffects {
    val all: List<PlayboxEffect> by lazy {
        listOf(
            eye(), beer(), neonVortex(), blackHole(), lightningStorm(),
            hyperspace(), interference(), cyberSkull(), spaceInvader(), orbitingComet(),
            liquidMetal(), pulse(), scanner(), rain(), fireworks(),
        )
    }

    private fun frame(duration: Int = 100, draw: IntArray.() -> Unit): EffectFrame =
        EffectFrame(IntArray(PIXEL_COUNT).apply(draw), duration).normalized()

    private fun IntArray.pixel(x: Int, y: Int, value: Int = 255) {
        if (x in 0 until MATRIX_SIZE && y in 0 until MATRIX_SIZE) {
            val index = y * MATRIX_SIZE + x
            if (PHONE_4A_PRO_MASK[index]) this[index] = value.coerceIn(0, 255)
        }
    }

    private fun eye(): PlayboxEffect {
        val frames = buildList {
            val pupilPositions = listOf(6 to 6, 4 to 6, 6 to 6, 8 to 6, 6 to 6)
            pupilPositions.forEach { (px, py) ->
                add(frame(170) {
                    for (x in 2..10) {
                        val edge = if (x in 4..8) 3 else 4
                        pixel(x, edge, 170)
                        pixel(x, 12 - edge, 170)
                    }
                    for (y in 5..7) for (x in 3..9) {
                        if (hypot((x - px).toDouble(), (y - py).toDouble()) <= 1.5) pixel(x, y)
                    }
                    pixel(px, py, 45)
                })
            }
            add(frame(90) { for (x in 2..10) pixel(x, 6, if (x in 4..8) 255 else 140) })
        }
        return PlayboxEffect("builtin-eye", "THE EYE", "Blinking, scanning pixel eye", frames, builtIn = true)
    }

    private fun beer(): PlayboxEffect {
        val frames = (0..8).map { step ->
            frame(140) {
                for (y in 3..11) {
                    pixel(3, y, 180); pixel(9, y, 180)
                    if (y == 11) for (x in 3..9) pixel(x, y, 180)
                }
                for (y in (11 - step)..10) for (x in 4..8) pixel(x, y, 210)
                if (step > 2) for (x in 4..8) pixel(x, (11 - step).coerceAtLeast(3), if (x % 2 == 0) 255 else 120)
                for (y in 5..9) { pixel(10, y, 150); pixel(11, y, 150) }
                pixel(10, 4, 150); pixel(10, 10, 150)
            }
        } + (7 downTo 0).map { step ->
            frame(125) {
                for (y in 3..11) { pixel(3, y, 180); pixel(9, y, 180) }
                for (x in 3..9) pixel(x, 11, 180)
                for (y in (11 - step)..10) for (x in 4..8) pixel(x, y, 210)
                for (y in 5..9) { pixel(10, y, 150); pixel(11, y, 150) }
            }
        }
        return PlayboxEffect("builtin-beer", "DRINK A BEER", "A tiny mug fills, foams and drains", frames, builtIn = true)
    }

    private fun pulse(): PlayboxEffect {
        val levels = listOf(28, 55, 100, 180, 255, 180, 100, 55)
        return PlayboxEffect("builtin-pulse", "PULSE", "Soft radial heartbeat", levels.map { level ->
            frame(if (level == 255) 180 else 90) {
                for (y in 0 until MATRIX_SIZE) for (x in 0 until MATRIX_SIZE) {
                    val distance = hypot((x - 6).toDouble(), (y - 6).toDouble())
                    val value = (level - distance * 22).roundToInt().coerceAtLeast(0)
                    pixel(x, y, value)
                }
            }
        }, builtIn = true)
    }

    private fun scanner(): PlayboxEffect = PlayboxEffect(
        "builtin-scanner", "SCANNER", "A bright radar sweep with a fading tail",
        (0 until MATRIX_SIZE).map { column ->
            frame(70) {
                for (y in 0 until MATRIX_SIZE) {
                    pixel(column, y, 255)
                    pixel(column - 1, y, 120)
                    pixel(column - 2, y, 45)
                }
            }
        }, builtIn = true,
    )

    private fun rain(): PlayboxEffect {
        val random = Random(404)
        val drops = List(13) { random.nextInt(13) to random.nextInt(13) }
        return PlayboxEffect("builtin-rain", "DIGITAL RAIN", "Falling trails across the matrix", (0..12).map { tick ->
            frame(90) {
                drops.forEachIndexed { index, (x, start) ->
                    val y = (start + tick + index / 3) % 13
                    pixel(x, y, 255); pixel(x, y - 1, 120); pixel(x, y - 2, 35)
                }
            }
        }, builtIn = true)
    }

    private fun fireworks(): PlayboxEffect {
        val centers = listOf(4 to 5, 8 to 7)
        return PlayboxEffect("builtin-fireworks", "FIREWORKS", "Two expanding sparks", (0..8).map { tick ->
            frame(100) {
                centers.forEachIndexed { index, (cx, cy) ->
                    val radius = ((tick - index * 2).coerceAtLeast(0) / 2.0)
                    if (radius == 0.0) pixel(cx, cy, 255)
                    for (y in 0 until 13) for (x in 0 until 13) {
                        val delta = abs(hypot((x - cx).toDouble(), (y - cy).toDouble()) - radius)
                        if (delta < .55) pixel(x, y, (255 - tick * 18).coerceAtLeast(45))
                    }
                }
            }
        }, builtIn = true)
    }

    private fun neonVortex(): PlayboxEffect = PlayboxEffect(
        "builtin-vortex", "NEON VORTEX", "A luminous spiral collapses into the center",
        (0 until 36).map { tick ->
            frame(55) {
                val phase = tick * PI * 2 / 36.0
                for (y in 0 until 13) for (x in 0 until 13) {
                    val dx = x - 6.0
                    val dy = y - 6.0
                    val radius = hypot(dx, dy)
                    val angle = atan2(dy, dx)
                    val arm = ((sin(radius * 1.75 - angle * 3.0 - phase * 2.0) + 1) / 2).pow(7)
                    val core = exp(-radius * radius / 4.0)
                    val fade = (1.0 - radius / 9.0).coerceIn(0.0, 1.0)
                    pixel(x, y, ((arm * 235 + core * 255) * fade).roundToInt())
                }
            }
        }, builtIn = true,
    )

    private fun blackHole(): PlayboxEffect = PlayboxEffect(
        "builtin-black-hole", "BLACK HOLE", "Spinning accretion disk with a light-devouring core",
        (0 until 32).map { tick ->
            frame(65) {
                val phase = tick * PI * 2 / 32.0
                for (y in 0 until 13) for (x in 0 until 13) {
                    val dx = x - 6.0
                    val dy = y - 6.0
                    val radius = hypot(dx, dy)
                    val angle = atan2(dy, dx)
                    val warpedRing = 3.6 + sin(angle * 2 - phase) * .7
                    val disk = exp(-abs(radius - warpedRing) * 1.8)
                    val streak = ((sin(angle * 5 + radius * 1.3 - phase * 3) + 1) / 2).pow(5)
                    val jet = if (abs(dx) < .65) exp(-abs(dy) / 3.5) * .55 else 0.0
                    val darkness = if (radius < 1.55) 0.0 else 1.0
                    pixel(x, y, ((disk * (90 + streak * 165) + jet * 180) * darkness).roundToInt())
                }
            }
        }, builtIn = true,
    )

    private fun lightningStorm(): PlayboxEffect {
        val random = Random(0xB01D)
        val frames = buildList {
            repeat(5) { storm ->
                val path = IntArray(13)
                path[0] = random.nextInt(4, 9)
                for (y in 1 until 13) path[y] = (path[y - 1] + random.nextInt(-1, 2)).coerceIn(1, 11)
                listOf(0, 255, 150, 45).forEachIndexed { stage, power ->
                    add(frame(if (stage == 0) 130 else 42) {
                        if (power > 0) {
                            for (y in 0 until 13) {
                                pixel(path[y], y, power)
                                pixel(path[y] - 1, y, power / 5)
                                pixel(path[y] + 1, y, power / 5)
                                if (y > 4 && (y + storm) % 4 == 0) {
                                    pixel(path[y] + 1, y + 1, power / 2)
                                    pixel(path[y] + 2, y + 2, power / 3)
                                }
                            }
                            if (stage == 1) for (i in indices) if (PHONE_4A_PRO_MASK[i] && this[i] == 0) this[i] = 18
                        }
                    })
                }
            }
        }
        return PlayboxEffect("builtin-lightning", "LIGHTNING STORM", "Branching bolts and retina flash", frames, builtIn = true)
    }

    private fun hyperspace(): PlayboxEffect {
        val random = Random(1977)
        val stars = List(18) { random.nextDouble(0.0, PI * 2) to random.nextDouble(.3, 6.3) }
        return PlayboxEffect("builtin-hyperspace", "HYPERSPACE", "Stars tear outward at impossible speed", (0 until 30).map { tick ->
            frame(55) {
                val travel = tick / 30.0 * 6.0
                stars.forEach { (angle, start) ->
                    val radius = (start + travel) % 6.5
                    val x = (6 + cos(angle) * radius).roundToInt()
                    val y = (6 + sin(angle) * radius).roundToInt()
                    val brightness = (70 + radius / 6.5 * 185).roundToInt()
                    pixel(x, y, brightness)
                    pixel((6 + cos(angle) * (radius - .9)).roundToInt(), (6 + sin(angle) * (radius - .9)).roundToInt(), brightness / 3)
                }
                pixel(6, 6, 255)
            }
        }, builtIn = true)
    }

    private fun interference(): PlayboxEffect = PlayboxEffect(
        "builtin-interference", "WAVE COLLIDER", "Two moving emitters collide into liquid geometry",
        (0 until 36).map { tick ->
            frame(65) {
                val phase = tick * PI * 2 / 36.0
                val ax = 6 + cos(phase) * 3.5
                val ay = 6 + sin(phase) * 3.5
                val bx = 6 + cos(phase + PI) * 3.5
                val by = 6 + sin(phase + PI) * 3.5
                for (y in 0 until 13) for (x in 0 until 13) {
                    val a = hypot(x - ax, y - ay)
                    val b = hypot(x - bx, y - by)
                    val wave = abs(sin(a * 1.7 - phase * 2) + sin(b * 1.7 - phase * 2)) / 2
                    pixel(x, y, wave.pow(4).times(255).roundToInt())
                }
            }
        }, builtIn = true,
    )

    private fun cyberSkull(): PlayboxEffect = PlayboxEffect(
        "builtin-skull", "CYBER SKULL", "A glitching skull wakes up and stares back",
        (0 until 24).map { tick ->
            frame(if (tick % 8 == 7) 45 else 90) {
                val shift = if (tick % 8 == 7) if (tick % 2 == 0) 1 else -1 else 0
                for (y in 1..10) for (x in 2..10) {
                    val dx = x - 6.0
                    val dy = y - 5.5
                    val shell = hypot(dx / 1.05, dy)
                    if (shell in 3.5..4.6 || (y in 8..10 && x in 4..8 && x % 2 == 0)) pixel(x + shift, y, 130)
                }
                val eyePower = (120 + 135 * ((sin(tick * PI / 6) + 1) / 2)).roundToInt()
                for (y in 4..6) { pixel(4 + shift, y, eyePower); pixel(8 + shift, y, eyePower) }
                pixel(5 + shift, 7, 90); pixel(6 + shift, 7, 180); pixel(7 + shift, 7, 90)
                if (tick % 8 == 7) for (x in 1..11) pixel(x, 3, if (x % 2 == 0) 255 else 40)
            }
        }, builtIn = true,
    )

    private fun spaceInvader(): PlayboxEffect {
        val sprite = arrayOf("..#...#..", "...#.#...", "..#####..", ".##.#.##.", "#########", "#.#...#.#", "...#.#...")
        return PlayboxEffect("builtin-invader", "LAST INVADER", "The final pixel invader strafes and fires", (0 until 28).map { tick ->
            frame(75) {
                val offset = (sin(tick * PI * 2 / 28) * 2).roundToInt()
                sprite.forEachIndexed { y, row -> row.forEachIndexed { x, char ->
                    if (char == '#') pixel(x + 2 + offset, y + 2, if ((tick / 3) % 2 == 0) 220 else 150)
                } }
                val shotY = (9 + tick % 6).coerceAtMost(12)
                pixel(6 + offset, shotY, 255); pixel(6 + offset, shotY - 1, 70)
            }
        }, builtIn = true)
    }

    private fun orbitingComet(): PlayboxEffect = PlayboxEffect(
        "builtin-comet", "ORBITAL COMET", "A blazing comet bends around a tiny sun",
        (0 until 36).map { tick ->
            frame(60) {
                val angle = tick * PI * 2 / 36.0
                pixel(6, 6, 180)
                repeat(6) { tail ->
                    val a = angle - tail * .14
                    val radius = 5.0 - tail * .12
                    pixel(
                        (6 + cos(a) * radius).roundToInt(),
                        (6 + sin(a) * radius).roundToInt(),
                        (255 * (1.0 - tail / 7.0)).roundToInt(),
                    )
                }
            }
        }, builtIn = true,
    )

    private fun liquidMetal(): PlayboxEffect = PlayboxEffect(
        "builtin-liquid-metal", "LIQUID METAL", "Chrome-like waves fold over themselves",
        (0 until 36).map { tick ->
            frame(65) {
                val phase = tick * PI * 2 / 36.0
                for (y in 0 until 13) for (x in 0 until 13) {
                    val field = sin(x * .72 + phase) + sin(y * .81 - phase * 1.3) + sin((x + y) * .43 + phase * .7)
                    val edge = exp(-abs(field) * 3.2)
                    val body = ((field + 3) / 6 * 70).coerceIn(0.0, 70.0)
                    pixel(x, y, (edge * 215 + body).roundToInt())
                }
            }
        }, builtIn = true,
    )
}
