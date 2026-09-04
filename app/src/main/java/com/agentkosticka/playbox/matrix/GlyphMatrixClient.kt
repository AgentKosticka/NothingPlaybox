package com.agentkosticka.playbox.matrix

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.StateFlow

sealed interface GlyphConnectionState {
    data object Simulator : GlyphConnectionState
    data object Connecting : GlyphConnectionState
    data object Ready : GlyphConnectionState
    data class Error(val message: String) : GlyphConnectionState
}

internal fun isPhone4aPro(manufacturer: String, brand: String, model: String): Boolean =
    (manufacturer.equals("Nothing", ignoreCase = true) || brand.equals("Nothing", ignoreCase = true)) &&
        model.equals("A069P", ignoreCase = true)

class GlyphMatrixClient(
    context: Context,
    private val connection: GlyphMatrixConnection,
) {
    private val appContext = context.applicationContext
    val state: StateFlow<GlyphConnectionState> = connection.state

    val isProbablySupported: Boolean
        get() = connection.isProbablySupported

    fun connect() {
        connection.acquire(GlyphMatrixConnection.User.APP)
    }

    fun showFrame(pixels: IntArray): Result<Unit> = connection.setAppFrame(pixels)

    fun close() {
        stopDisplay()
        connection.release(GlyphMatrixConnection.User.APP)
    }

    fun stopDisplay() {
        connection.closeAppMatrix()
    }

    fun openAodToyManager(): Result<Unit> = runCatching {
        val intent = Intent().apply {
            component = android.content.ComponentName(
                "com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.AodToySelectActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
