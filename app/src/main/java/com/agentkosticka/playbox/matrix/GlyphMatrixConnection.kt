package com.agentkosticka.playbox.matrix

import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the SDK's process-wide [GlyphMatrixManager] singleton.
 *
 * The app preview and Glyph Toy service can be alive at the same time. Calling
 * init/unInit independently from both replaces the SDK callback and can tear
 * down the AOD connection, so both clients take a lease on this shared owner.
 */
class GlyphMatrixConnection(context: Context) {
    private val appContext = context.applicationContext
    private val users = mutableSetOf<User>()
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
            _state.value = GlyphConnectionState.Error("Glyph Matrix service disconnected")
        }
    }

    fun acquire(user: User) {
        if (!isProbablySupported) {
            _state.value = GlyphConnectionState.Simulator
            return
        }
        if (!users.add(user) || users.size > 1) return

        _state.value = GlyphConnectionState.Connecting
        runCatching {
            GlyphMatrixManager.getInstance(appContext).also {
                manager = it
                it.init(callback)
            }
        }.onFailure {
            manager = null
            users.remove(user)
            _state.value = GlyphConnectionState.Error(it.message ?: "Glyph Matrix is unavailable")
        }
    }

    fun release(user: User) {
        if (!users.remove(user) || users.isNotEmpty()) return
        runCatching { manager?.turnOff() }
        runCatching { manager?.unInit() }
        manager = null
        _state.value = GlyphConnectionState.Simulator
    }

    fun setAppFrame(pixels: IntArray): Result<Unit> = runCatching {
        check(_state.value == GlyphConnectionState.Ready) { "Glyph Matrix is not connected" }
        manager?.setAppMatrixFrame(HardwareFrameEncoder.encode(pixels))
            ?: error("Glyph Matrix service is unavailable")
    }.onFailure { _state.value = GlyphConnectionState.Error(it.message ?: "Unable to display frame") }

    fun setToyFrame(pixels: IntArray): Result<Unit> = runCatching {
        check(_state.value == GlyphConnectionState.Ready) { "Glyph Matrix is not connected" }
        manager?.setMatrixFrame(HardwareFrameEncoder.encode(pixels))
            ?: error("Glyph Matrix service is unavailable")
    }

    fun closeAppMatrix() {
        runCatching { manager?.closeAppMatrix() }
    }

    enum class User { APP, TOY }
}
