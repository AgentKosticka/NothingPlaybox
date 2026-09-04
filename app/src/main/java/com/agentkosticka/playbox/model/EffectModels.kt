package com.agentkosticka.playbox.model

import java.util.UUID

const val MATRIX_SIZE = 13
const val PIXEL_COUNT = MATRIX_SIZE * MATRIX_SIZE
const val MAX_EFFECT_FRAMES = 600

/** Physical Phone (4a) Pro mask: 137 active LEDs in the 13x13 address space. */
val PHONE_4A_PRO_MASK: BooleanArray = BooleanArray(PIXEL_COUNT).also { mask ->
    val widths = intArrayOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5)
    widths.forEachIndexed { row, width ->
        val start = (MATRIX_SIZE - width) / 2
        repeat(width) { column -> mask[row * MATRIX_SIZE + start + column] = true }
    }
}

enum class LoopMode { LOOP, PING_PONG, HOLD }

data class EffectFrame(
    val pixels: IntArray = IntArray(PIXEL_COUNT),
    val durationMs: Int = 120,
) {
    init {
        require(pixels.size == PIXEL_COUNT) { "A frame must contain $PIXEL_COUNT pixels" }
        require(durationMs in 33..5_000) { "Frame duration must be 33..5000 ms" }
    }

    fun normalized(): EffectFrame = copy(
        pixels = IntArray(PIXEL_COUNT) { index ->
            if (PHONE_4A_PRO_MASK[index]) pixels[index].coerceIn(0, 255) else 0
        },
        durationMs = durationMs.coerceIn(33, 5_000),
    )

    override fun equals(other: Any?): Boolean =
        other is EffectFrame && durationMs == other.durationMs && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * durationMs + pixels.contentHashCode()
}

data class PlayboxEffect(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val frames: List<EffectFrame>,
    val loopMode: LoopMode = LoopMode.LOOP,
    val builtIn: Boolean = false,
    val procedural: ProceduralSpec? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(frames.isNotEmpty()) { "An effect needs at least one frame" }
        require(frames.size <= MAX_EFFECT_FRAMES) { "An effect supports at most $MAX_EFFECT_FRAMES frames" }
    }

    val isAnimated: Boolean get() = procedural != null || frames.size > 1
    val totalDurationMs: Int get() = frames.sumOf { it.durationMs }

    fun editableCopy(newName: String = "$name copy") = copy(
        id = UUID.randomUUID().toString(),
        name = newName,
        frames = frames.map { it.copy(pixels = it.pixels.copyOf()) },
        procedural = procedural?.deepCopy(),
        builtIn = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )
}

fun blankEffect(name: String = "Untitled", animated: Boolean = false): PlayboxEffect = PlayboxEffect(
    name = name,
    frames = if (animated) {
        listOf(EffectFrame(), EffectFrame())
    } else {
        listOf(EffectFrame())
    },
)

fun PlayboxEffect.frameIndexAt(elapsedMs: Long): Int {
    if (frames.size == 1) return 0
    val order = when (loopMode) {
        LoopMode.PING_PONG -> frames.indices.toList() + (frames.lastIndex - 1 downTo 1).toList()
        else -> frames.indices.toList()
    }
    val cycleDuration = order.sumOf { frames[it].durationMs }.coerceAtLeast(1)
    var cursor = when (loopMode) {
        LoopMode.HOLD -> elapsedMs.coerceAtMost((cycleDuration - 1).toLong()).toInt()
        else -> (elapsedMs % cycleDuration).toInt()
    }
    for (index in order) {
        val duration = frames[index].durationMs
        if (cursor < duration) return index
        cursor -= duration
    }
    return order.last()
}
