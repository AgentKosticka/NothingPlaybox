package com.agentkosticka.playbox.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    @Test
    fun historyStaysBoundToEditedFrame() {
        val history = EditorHistory()
        history.record(0, intArrayOf(1, 2))
        history.record(1, intArrayOf(3, 4))

        assertTrue(history.canUndo(0))
        assertTrue(history.canUndo(1))
        assertArrayEquals(intArrayOf(1, 2), history.undo(0, intArrayOf(9, 9)))
        assertFalse(history.canUndo(0))
        assertTrue(history.canUndo(1))
        assertArrayEquals(intArrayOf(9, 9), history.redo(0, intArrayOf(8, 8)))
    }
}
