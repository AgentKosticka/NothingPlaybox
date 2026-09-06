package com.agentkosticka.playbox.matrix

/** Small deterministic primitive behind process-wide Glyph ownership. */
internal class LeaseCounter<T> {
    private val counts = mutableMapOf<T, Int>()

    val total: Int get() = counts.values.sum()

    fun acquire(owner: T): Int {
        counts[owner] = (counts[owner] ?: 0) + 1
        return total
    }

    fun release(owner: T): Release {
        val current = counts[owner] ?: return Release(released = false, lastForOwner = false, total = total)
        val last = current == 1
        if (last) counts.remove(owner) else counts[owner] = current - 1
        return Release(released = true, lastForOwner = last, total = total)
    }

    fun count(owner: T): Int = counts[owner] ?: 0

    data class Release(val released: Boolean, val lastForOwner: Boolean, val total: Int)
}
