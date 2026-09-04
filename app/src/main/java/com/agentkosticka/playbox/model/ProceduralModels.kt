package com.agentkosticka.playbox.model

sealed interface ProceduralSpec {
    val frameDurationMs: Int

    data class ConwayLife(
        override val frameDurationMs: Int = 140,
        val initialState: IntArray,
    ) : ProceduralSpec {
        init {
            require(frameDurationMs in 67..2_000) { "Conway step duration must be 67..2000 ms" }
            require(initialState.size == PIXEL_COUNT) { "Conway seed must contain $PIXEL_COUNT cells" }
        }

        fun normalized() = copy(
            frameDurationMs = frameDurationMs.coerceIn(67, 2_000),
            initialState = IntArray(PIXEL_COUNT) { index ->
                if (PHONE_4A_PRO_MASK[index] && initialState[index] > 0) 255 else 0
            },
        )

        override fun equals(other: Any?): Boolean =
            other is ConwayLife && frameDurationMs == other.frameDurationMs && initialState.contentEquals(other.initialState)

        override fun hashCode(): Int = 31 * frameDurationMs + initialState.contentHashCode()
    }

    data class ShiftingNoise(
        override val frameDurationMs: Int = 67,
        val seed: Long = 0x51F7L,
        val speed: Float = 1f,
        val scale: Float = 1f,
        val detail: Float = 0.35f,
    ) : ProceduralSpec {
        fun normalized() = copy(
            frameDurationMs = frameDurationMs.coerceIn(67, 500),
            speed = speed.coerceIn(0.15f, 4f),
            scale = scale.coerceIn(0.45f, 2.2f),
            detail = detail.coerceIn(0f, 1f),
        )
    }

    data class LavaLamp(
        override val frameDurationMs: Int = 67,
        val seed: Long = 0x1A7A1A7AL,
        val speed: Float = 1f,
        val blobCount: Int = 4,
        val softness: Float = 0.18f,
    ) : ProceduralSpec {
        fun normalized() = copy(
            frameDurationMs = frameDurationMs.coerceIn(67, 500),
            speed = speed.coerceIn(0.15f, 4f),
            blobCount = blobCount.coerceIn(2, 8),
            softness = softness.coerceIn(0.08f, 0.45f),
        )
    }
}

fun ProceduralSpec.normalized(): ProceduralSpec = when (this) {
    is ProceduralSpec.ConwayLife -> normalized()
    is ProceduralSpec.ShiftingNoise -> normalized()
    is ProceduralSpec.LavaLamp -> normalized()
}

fun ProceduralSpec.deepCopy(): ProceduralSpec = when (this) {
    is ProceduralSpec.ConwayLife -> copy(initialState = initialState.copyOf())
    is ProceduralSpec.ShiftingNoise -> copy()
    is ProceduralSpec.LavaLamp -> copy()
}
