package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.*
import kotlin.math.roundToInt

/** The same renderer powers the real toy service and the AOD tab's preview. */
class AodPlayback(private val effects: List<PlayboxEffect>, private val selectedId: String?, private val settings: AodSettings) {
    private var runtime: ProceduralEffectRuntime? = null
    private var currentSlot = -1L
    fun frameAt(elapsedMs: Long, localHour: Int): EffectFrame {
        if (!settings.enabled || settings.isQuiet(localHour) || effects.isEmpty()) return EffectFrame(durationMs = 500)
        val playlist = if (settings.rotate) effects.filter { it.id in settings.rotationIds } else emptyList()
        val slot = if (playlist.isEmpty()) 0L else elapsedMs.coerceAtLeast(0) / (settings.rotationSeconds * 1000L)
        val effect = if (playlist.isEmpty()) effects.firstOrNull { it.id == selectedId } ?: effects.first() else playlist[(slot % playlist.size).toInt()]
        if (currentSlot != slot) {
            currentSlot = slot
            runtime = effect.procedural?.let { ProceduralEffectRuntime(effect) }
        }
        val localElapsed = if (playlist.isEmpty()) elapsedMs else elapsedMs % (settings.rotationSeconds * 1000L)
        val scaledTime = (localElapsed.coerceAtLeast(0) * settings.speed).toLong()
        val original = runtime?.frameAt(scaledTime) ?: effect.frames[effect.frameIndexAt(scaledTime)]
        return EffectFrame(IntArray(PIXEL_COUNT) { (original.pixels[it] * settings.brightness).roundToInt() },
            (original.durationMs / settings.speed).roundToInt().coerceIn(67, 5000))
    }
}
