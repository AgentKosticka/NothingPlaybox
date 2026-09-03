package com.agentkosticka.playbox.ui

import com.agentkosticka.playbox.model.MATRIX_SIZE
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MatrixPathTest {
    @Test
    fun horizontalStrokeFillsEveryCrossedCell() {
        val row = 6
        val from = row * MATRIX_SIZE + 2
        val to = row * MATRIX_SIZE + 10

        assertArrayEquals(
            IntArray(9) { from + it },
            matrixLineIndices(from, to),
        )
    }

    @Test
    fun diagonalStrokeDoesNotLeaveHoles() {
        val from = 4 * MATRIX_SIZE + 2
        val to = 10 * MATRIX_SIZE + 8

        assertArrayEquals(
            intArrayOf(
                4 * MATRIX_SIZE + 2,
                5 * MATRIX_SIZE + 3,
                6 * MATRIX_SIZE + 4,
                7 * MATRIX_SIZE + 5,
                8 * MATRIX_SIZE + 6,
                9 * MATRIX_SIZE + 7,
                10 * MATRIX_SIZE + 8,
            ),
            matrixLineIndices(from, to),
        )
    }

    @Test
    fun inactiveCornerCellsAreFilteredFromStroke() {
        assertArrayEquals(
            intArrayOf(4, 5, 6, 7, 8),
            matrixLineIndices(0, 12),
        )
    }
}
