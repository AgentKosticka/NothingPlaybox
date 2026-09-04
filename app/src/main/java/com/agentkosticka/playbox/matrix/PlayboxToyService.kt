package com.agentkosticka.playbox.matrix

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.agentkosticka.playbox.PlayboxApplication
import com.agentkosticka.playbox.data.EffectCatalog
import com.agentkosticka.playbox.data.ProceduralEffectRuntime
import com.agentkosticka.playbox.model.frameIndexAt
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

class PlayboxToyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playback: Job? = null
    private var readiness: Job? = null
    private var connection: GlyphMatrixConnection? = null
    private var playbackRequested = false

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == GlyphToy.MSG_GLYPH_TOY) {
                when (message.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    GlyphToy.EVENT_AOD, GlyphToy.EVENT_CHANGE -> {
                        playbackRequested = true
                        startPlayback()
                    }
                }
            } else super.handleMessage(message)
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        if (!isPhone4aPro(Build.MANUFACTURER, Build.BRAND, Build.MODEL)) return messenger.binder
        connection = (application as PlayboxApplication).glyphConnection.also {
            playbackRequested = true
            readiness = scope.launch {
                it.state.collectLatest { state ->
                    if (state == GlyphConnectionState.Ready && playbackRequested) startPlayback()
                    else if (state is GlyphConnectionState.Error) playback?.cancel()
                }
            }
            it.acquire(GlyphMatrixConnection.User.TOY)
        }
        return messenger.binder
    }

    private fun startPlayback() {
        val currentConnection = connection ?: return
        if (currentConnection.state.value != GlyphConnectionState.Ready) return
        playback?.cancel()
        val repository = (application as PlayboxApplication).repository
        val effect = repository.activeEffectId?.let(repository::find) ?: EffectCatalog.builtIns.first()
        val proceduralRuntime = effect.procedural?.let { ProceduralEffectRuntime(effect) }
        playback = scope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - started
                val frame = proceduralRuntime?.frameAt(elapsed)
                    ?: effect.frames[effect.frameIndexAt(elapsed)]
                withContext(Dispatchers.Main.immediate) {
                    currentConnection.setToyFrame(frame.pixels)
                }
                delay(frame.durationMs.coerceAtLeast(67).toLong())
                if (proceduralRuntime == null &&
                    effect.loopMode == com.agentkosticka.playbox.model.LoopMode.HOLD &&
                    elapsed >= effect.totalDurationMs) break
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        playbackRequested = false
        playback?.cancel()
        readiness?.cancel()
        connection?.release(GlyphMatrixConnection.User.TOY)
        connection = null
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
