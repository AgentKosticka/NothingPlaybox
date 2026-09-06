package com.agentkosticka.playbox

import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.PlayboxEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayboxViewModelTest {
    @Test
    fun draftIsDeepCopiedAndSurvivesUiRebinding() {
        val pixels = IntArray(169).also { it[84] = 123 }
        val source = PlayboxEffect(name = "Draft", frames = listOf(EffectFrame(pixels)))
        val model = PlayboxViewModel()

        model.beginEdit(source)
        val firstBinding = model.editorDraft!!
        source.frames.single().pixels[84] = 0
        val secondBinding = model.editorDraft!!

        assertEquals(source.id, secondBinding.id)
        assertEquals(123, secondBinding.frames.single().pixels[84])
        assertNotSame(source.frames.single().pixels, secondBinding.frames.single().pixels)
        assertTrue(firstBinding === secondBinding)
    }

    @Test
    fun updateOnlyAcceptsCurrentDraftAndDiscardClearsIt() {
        val current = PlayboxEffect(name = "Current", frames = listOf(EffectFrame()))
        val other = PlayboxEffect(name = "Other", frames = listOf(EffectFrame()))
        val model = PlayboxViewModel()
        model.beginEdit(current)

        model.updateDraft(other.copy(name = "Wrong"))
        assertEquals("Current", model.editorDraft?.name)

        model.updateDraft(current.copy(name = "Updated"))
        assertEquals("Updated", model.editorDraft?.name)
        model.clearEditor()
        assertNull(model.editorDraft)
    }
}
