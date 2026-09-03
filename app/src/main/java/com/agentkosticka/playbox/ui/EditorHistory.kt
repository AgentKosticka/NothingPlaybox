package com.agentkosticka.playbox.ui

/**
 * Undo/redo state bound to a specific animation frame so edits cannot leak
 * across frames when the user navigates the timeline.
 */
internal class EditorHistory(private val limit: Int = 50) {
    private data class Entry(val frameIndex: Int, val pixels: IntArray)

    private val undo = ArrayDeque<Entry>()
    private val redo = ArrayDeque<Entry>()

    fun record(frameIndex: Int, pixels: IntArray) {
        undo.addLast(Entry(frameIndex, pixels.copyOf()))
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(frameIndex: Int, currentPixels: IntArray): IntArray? {
        val entry = undo.removeLastMatching(frameIndex) ?: return null
        redo.addLast(Entry(frameIndex, currentPixels.copyOf()))
        return entry.pixels.copyOf()
    }

    fun redo(frameIndex: Int, currentPixels: IntArray): IntArray? {
        val entry = redo.removeLastMatching(frameIndex) ?: return null
        undo.addLast(Entry(frameIndex, currentPixels.copyOf()))
        return entry.pixels.copyOf()
    }

    fun canUndo(frameIndex: Int): Boolean = undo.any { it.frameIndex == frameIndex }
    fun canRedo(frameIndex: Int): Boolean = redo.any { it.frameIndex == frameIndex }

    private fun ArrayDeque<Entry>.removeLastMatching(frameIndex: Int): Entry? {
        for (index in lastIndex downTo 0) {
            val entry = elementAt(index)
            if (entry.frameIndex == frameIndex) {
                remove(entry)
                return entry
            }
        }
        return null
    }
}
