package com.agentkosticka.playbox.matrix

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface GlyphConnectionState {
    data object Simulator : GlyphConnectionState
    data object Connecting : GlyphConnectionState
    data object Ready : GlyphConnectionState
    data class Error(val message: String) : GlyphConnectionState
}

internal fun isPhone4aPro(manufacturer: String, brand: String, model: String): Boolean =
    (manufacturer.equals("Nothing", ignoreCase = true) || brand.equals("Nothing", ignoreCase = true)) &&
        model.equals("A069P", ignoreCase = true)

class GlyphMatrixClient(context: Context) {
    private val appContext = context.applicationContext
    private var manager: GlyphMatrixManager? = null
    private val _state = MutableStateFlow<GlyphConnectionState>(GlyphConnectionState.Simulator)
    val state: StateFlow<GlyphConnectionState> = _state.asStateFlow()

    val isProbablySupported: Boolean
        get() = isPhone4aPro(Build.MANUFACTURER, Build.BRAND, Build.MODEL)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            val registered = runCatching { manager?.register(Glyph.DEVICE_25111p) == true }
                .getOrDefault(false)
            _state.value = if (registered) GlyphConnectionState.Ready
            else GlyphConnectionState.Error("Glyph Matrix registration was rejected")
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            manager = null
            _state.value = GlyphConnectionState.Error("Glyph Matrix service disconnected")
        }
    }

    fun connect() {
        if (manager != null && _state.value !is GlyphConnectionState.Error) return
        if (_state.value is GlyphConnectionState.Error) {
            runCatching { manager?.unInit() }
            manager = null
        }
        if (!isProbablySupported) {
            _state.value = GlyphConnectionState.Simulator
            return
        }
        _state.value = GlyphConnectionState.Connecting
        runCatching {
            GlyphMatrixManager.getInstance(appContext).also {
                manager = it
                it.init(callback)
            }
        }.onFailure {
            manager = null
            _state.value = GlyphConnectionState.Error(it.message ?: "Glyph Matrix is unavailable")
        }
    }

    fun showFrame(pixels: IntArray): Result<Unit> = runCatching {
        check(_state.value == GlyphConnectionState.Ready) { "Glyph Matrix is not connected" }
        manager?.setAppMatrixFrame(HardwareFrameEncoder.encode(pixels))
            ?: error("Glyph Matrix service is unavailable")
    }.onFailure { _state.value = GlyphConnectionState.Error(it.message ?: "Unable to display frame") }

    fun close() {
        stopDisplay()
        runCatching { manager?.unInit() }
        manager = null
        _state.value = GlyphConnectionState.Simulator
    }

    fun stopDisplay() {
        runCatching { manager?.closeAppMatrix() }
    }

    fun openToyManager(): Result<Unit> = runCatching {
        val intent = Intent().apply {
            component = ComponentName(
                "com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
