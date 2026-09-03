package com.agentkosticka.playbox

import android.app.Application
import com.agentkosticka.playbox.data.EffectRepository
import com.agentkosticka.playbox.matrix.GlyphMatrixClient

class PlayboxApplication : Application() {
    val repository: EffectRepository by lazy { EffectRepository(this) }
    val glyphClient: GlyphMatrixClient by lazy { GlyphMatrixClient(this) }
}
