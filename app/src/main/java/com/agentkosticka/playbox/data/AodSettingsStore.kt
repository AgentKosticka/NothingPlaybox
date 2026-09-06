package com.agentkosticka.playbox.data

import android.content.Context
import androidx.core.content.edit
import com.agentkosticka.playbox.model.AodSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AodSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("aod-controls", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(AodSettings(
        enabled = prefs.getBoolean("enabled", true), brightness = prefs.getFloat("brightness", 1f), speed = prefs.getFloat("speed", 1f),
        rotate = prefs.getBoolean("rotate", false), rotationSeconds = prefs.getInt("seconds", 30),
        rotationIds = prefs.getStringSet("ids", emptySet())!!.toSet(), quietHours = prefs.getBoolean("quiet", false),
        quietStart = prefs.getInt("start", 22), quietEnd = prefs.getInt("end", 7),
    ).normalized())
    val settings = state.asStateFlow()
    fun save(value: AodSettings) {
        val next = value.normalized()
        prefs.edit {
            putBoolean("enabled", next.enabled); putFloat("brightness", next.brightness); putFloat("speed", next.speed)
            putBoolean("rotate", next.rotate); putInt("seconds", next.rotationSeconds); putStringSet("ids", next.rotationIds)
            putBoolean("quiet", next.quietHours); putInt("start", next.quietStart); putInt("end", next.quietEnd)
        }
        state.value = next
    }
}
