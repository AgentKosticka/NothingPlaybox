package com.agentkosticka.playbox.matrix

import android.app.Service
import android.content.ComponentName
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
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayboxToyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playback: Job? = null
    private var manager: GlyphMatrixManager? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == GlyphToy.MSG_GLYPH_TOY) {
                when (message.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    GlyphToy.EVENT_AOD, GlyphToy.EVENT_CHANGE -> startPlayback()
                }
            } else super.handleMessage(message)
        }
    }
    private val messenger = Messenger(handler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            if (runCatching { manager?.register(Glyph.DEVICE_25111p) == true }.getOrDefault(false)) {
                startPlayback()
            }
        }
        override fun onServiceDisconnected(componentName: ComponentName?) { playback?.cancel() }
    }

    override fun onBind(intent: Intent?): IBinder {
        if (!isPhone4aPro(Build.MANUFACTURER, Build.BRAND, Build.MODEL)) return messenger.binder
        manager = GlyphMatrixManager.getInstance(applicationContext)
        manager?.init(callback)
        return messenger.binder
    }

    private fun startPlayback() {
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
                    runCatching { manager?.setMatrixFrame(HardwareFrameEncoder.encode(frame.pixels)) }
                }
                delay(frame.durationMs.coerceAtLeast(67).toLong())
                if (proceduralRuntime == null &&
                    effect.loopMode == com.agentkosticka.playbox.model.LoopMode.HOLD &&
                    elapsed >= effect.totalDurationMs) break
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        playback?.cancel()
        runCatching { manager?.turnOff() }
        runCatching { manager?.unInit() }
        manager = null
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
