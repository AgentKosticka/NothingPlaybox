package com.agentkosticka.playbox.ui

import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import kotlin.math.abs

/** Returns active matrix pixels touched by a straight grid line, including both endpoints. */
internal fun matrixLineIndices(fromIndex: Int, toIndex: Int): IntArray {
    require(fromIndex in 0 until PIXEL_COUNT)
    require(toIndex in 0 until PIXEL_COUNT)

    var x = fromIndex % MATRIX_SIZE
    var y = fromIndex / MATRIX_SIZE
    val targetX = toIndex % MATRIX_SIZE
    val targetY = toIndex / MATRIX_SIZE
    val dx = abs(targetX - x)
    val dy = abs(targetY - y)
    val stepX = if (x < targetX) 1 else -1
    val stepY = if (y < targetY) 1 else -1
    var error = dx - dy
    val result = ArrayList<Int>(maxOf(dx, dy) + 1)

    while (true) {
        val index = y * MATRIX_SIZE + x
        if (PHONE_4A_PRO_MASK[index]) result += index
        if (x == targetX && y == targetY) break
        val twiceError = error * 2
        if (twiceError > -dy) {
            error -= dy
            x += stepX
        }
        if (twiceError < dx) {
            error += dx
            y += stepY
        }
    }

    return result.toIntArray()
}
