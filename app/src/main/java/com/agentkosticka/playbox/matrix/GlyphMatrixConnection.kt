package com.agentkosticka.playbox.matrix

import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the SDK's process-wide [GlyphMatrixManager] singleton.
 *
 * APP and TOY may coexist, and multiple Activity instances can transiently overlap. Leases are
 * reference-counted so one owner cannot tear down a manager still in use by another owner.
 */
class GlyphMatrixConnection(context: Context) {
    private val appContext = context.applicationContext
    private val leases = LeaseCounter<User>()
    private var manager: GlyphMatrixManager? = null
    private val _state = MutableStateFlow<GlyphConnectionState>(GlyphConnectionState.Simulator)
    val state: StateFlow<GlyphConnectionState> = _state.asStateFlow()

    val isProbablySupported: Boolean
        get() = isPhone4aPro(Build.MANUFACTURER, Build.BRAND, Build.MODEL)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            synchronized(this@GlyphMatrixConnection) {
                val registered = runCatching { manager?.register(Glyph.DEVICE_25111p) == true }
                    .getOrDefault(false)
                _state.value = if (registered) GlyphConnectionState.Ready
                else GlyphConnectionState.Error("Glyph Matrix registration was rejected")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            synchronized(this@GlyphMatrixConnection) {
                _state.value = GlyphConnectionState.Error("Glyph Matrix service disconnected")
                if (leases.total > 0) initializeLocked()
            }
        }
    }

    @Synchronized
    fun acquire(user: User) {
        if (!isProbablySupported) {
            _state.value = GlyphConnectionState.Simulator
            return
        }
        val total = leases.acquire(user)
        if (total == 1 || manager == null || _state.value is GlyphConnectionState.Error) {
            initializeLocked()
        }
    }

    @Synchronized
    fun release(user: User) {
        val release = leases.release(user)
        if (!release.released) return

        if (user == User.TOY && release.lastForOwner && release.total > 0) {
            // Keep the SDK connection for APP, but do not leave the last AOD/toy frame resident.
            if (_state.value == GlyphConnectionState.Ready) {
                runCatching { manager?.setMatrixFrame(HardwareFrameEncoder.encode(IntArray(PIXEL_COUNT))) }
            }
        }

        if (release.total == 0) shutdownLocked()
    }

    fun setAppFrame(pixels: IntArray): Result<Unit> = setFrame(
        notReadyMessage = "Glyph Matrix is not connected",
        operation = { it.setAppMatrixFrame(HardwareFrameEncoder.encode(pixels)) },
    )

    fun setToyFrame(pixels: IntArray): Result<Unit> = setFrame(
        notReadyMessage = "Glyph Matrix is not connected",
        operation = { it.setMatrixFrame(HardwareFrameEncoder.encode(pixels)) },
    )

    private fun setFrame(
        notReadyMessage: String,
        operation: (GlyphMatrixManager) -> Unit,
    ): Result<Unit> {
        val currentManager = synchronized(this) {
            if (_state.value != GlyphConnectionState.Ready) return Result.failure(IllegalStateException(notReadyMessage))
            manager ?: return Result.failure(IllegalStateException("Glyph Matrix service is unavailable"))
        }
        return runCatching { operation(currentManager) }
            .onFailure { failure ->
                synchronized(this) {
                    _state.value = GlyphConnectionState.Error(failure.message ?: "Unable to display frame")
                    if (leases.total > 0) initializeLocked()
                }
            }
    }

    fun closeAppMatrix() {
        val current = synchronized(this) { manager }
        runCatching { current?.closeAppMatrix() }
    }

    @Synchronized
    internal fun leaseCount(user: User): Int = leases.count(user)

    private fun initializeLocked() {
        runCatching { manager?.unInit() }
        manager = null
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

    private fun shutdownLocked() {
        runCatching { manager?.turnOff() }
        runCatching { manager?.unInit() }
        manager = null
        _state.value = GlyphConnectionState.Simulator
    }

    enum class User { APP, TOY }
}
