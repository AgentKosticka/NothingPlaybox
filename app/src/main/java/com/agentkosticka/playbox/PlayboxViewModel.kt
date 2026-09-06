package com.agentkosticka.playbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.deepCopy

/** Keeps unsaved editor work outside the Activity/Compose instance so configuration recreation is lossless. */
class PlayboxViewModel : ViewModel() {
    var editorDraft: PlayboxEffect? by mutableStateOf(null)
        private set

    fun beginEdit(effect: PlayboxEffect) {
        editorDraft = effect.deepCopyForDraft()
    }

    fun updateDraft(effect: PlayboxEffect) {
        if (editorDraft?.id == effect.id) editorDraft = effect.deepCopyForDraft()
    }

    fun clearEditor() {
        editorDraft = null
    }
}

private fun PlayboxEffect.deepCopyForDraft(): PlayboxEffect = copy(
    frames = frames.map { it.copy(pixels = it.pixels.copyOf()) },
    procedural = procedural?.deepCopy(),
)
