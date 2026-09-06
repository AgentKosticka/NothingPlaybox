package com.agentkosticka.playbox.data

import com.agentkosticka.playbox.model.PlayboxEffect

/** Single catalog consumed by storage and playback; effect families can live in focused files. */
object EffectCatalog {
    val builtIns: List<PlayboxEffect> by lazy {
        BuiltInEffects.all + ExpandedEffects.animations + ProceduralEffects.all + OrganicEffects.all + ExpandedEffects.engines
    }
}
