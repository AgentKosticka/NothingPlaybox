package com.agentkosticka.playbox.matrix

/**
 * Playbox stores portable, UI-friendly 8-bit intensities. Nothing's raw Matrix
 * frame API uses an 11-bit LED value, so conversion belongs at the SDK edge.
 */
object HardwareFrameEncoder {
    const val EDITOR_MAX = 255
    const val HARDWARE_MAX = 2047

    fun encode(editorPixels: IntArray): IntArray = IntArray(editorPixels.size) { index ->
        val value = editorPixels[index].coerceIn(0, EDITOR_MAX)
        (value * HARDWARE_MAX + EDITOR_MAX / 2) / EDITOR_MAX
    }
}
