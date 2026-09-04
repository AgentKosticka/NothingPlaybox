package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.ProceduralSpec

/** Organic effects whose simulation is evaluated live instead of baked into stored frame sequences. */
object OrganicEffects {
    val all: List<PlayboxEffect> by lazy {
        listOf(reactionDiffusion())
    }

    private fun reactionDiffusion(): PlayboxEffect {
        val spec = ProceduralSpec.OrganicBloom()
        val shell = PlayboxEffect(
            id = "builtin-reaction-diffusion",
            name = "ORGANIC BLOOM",
            description = "Live reaction-diffusion growth with adjustable evolution, growth, split and seed",
            frames = listOf(EffectFrame(durationMs = spec.frameDurationMs)),
            procedural = spec,
            builtIn = true,
        )
        return shell.copy(frames = listOf(ProceduralEffectRuntime(shell).frameAt(0)))
    }
}
