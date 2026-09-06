package com.agentkosticka.playbox.model

data class AodSettings(
    val enabled: Boolean = true,
    val brightness: Float = 1f,
    val speed: Float = 1f,
    val rotate: Boolean = false,
    val rotationSeconds: Int = 30,
    val rotationIds: Set<String> = emptySet(),
    val quietHours: Boolean = false,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
) {
    fun normalized() = copy(brightness = brightness.coerceIn(.05f, 1f), speed = speed.coerceIn(.25f, 2f),
        rotationSeconds = rotationSeconds.coerceIn(10, 300), quietStart = quietStart.coerceIn(0, 23), quietEnd = quietEnd.coerceIn(0, 23))
    fun isQuiet(hour: Int): Boolean = quietHours && when {
        quietStart == quietEnd -> true
        quietStart < quietEnd -> hour in quietStart until quietEnd
        else -> hour >= quietStart || hour < quietEnd
    }
}
