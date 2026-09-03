package com.agentkosticka.playbox

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.LoopMode
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.frameIndexAt
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPlaybackRegressionTest {
    @Test
    fun pingPongPlaybackUsesSharedTimingModel() {
        val effect = PlayboxEffect(
            name = "ping pong",
            frames = listOf(
                EffectFrame(durationMs = 100),
                EffectFrame(durationMs = 100),
                EffectFrame(durationMs = 100),
            ),
            loopMode = LoopMode.PING_PONG,
        )

        assertEquals(0, effect.frameIndexAt(0))
        assertEquals(1, effect.frameIndexAt(100))
        assertEquals(2, effect.frameIndexAt(200))
        assertEquals(1, effect.frameIndexAt(300))
        assertEquals(0, effect.frameIndexAt(400))
    }
}
