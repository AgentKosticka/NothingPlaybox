package com.agentkosticka.playbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import kotlin.math.min

@Composable
fun MatrixDisplay(
    pixels: IntArray,
    modifier: Modifier = Modifier,
    onPixel: ((Int) -> Unit)? = null,
) {
    fun indexAt(offset: Offset, size: IntSize): Int? {
        val side = min(size.width, size.height).toFloat()
        val originX = (size.width - side) / 2f
        val originY = (size.height - side) / 2f
        val cell = side / MATRIX_SIZE
        val x = ((offset.x - originX) / cell).toInt()
        val y = ((offset.y - originY) / cell).toInt()
        if (x !in 0 until MATRIX_SIZE || y !in 0 until MATRIX_SIZE) return null
        return (y * MATRIX_SIZE + x).takeIf { PHONE_4A_PRO_MASK[it] }
    }

    var canvasModifier = modifier.semantics { contentDescription = "13 by 13 Glyph Matrix preview" }
    if (onPixel != null) {
        canvasModifier = canvasModifier
            .pointerInput(onPixel) {
                detectTapGestures { offset -> indexAt(offset, size)?.let(onPixel) }
            }
            .pointerInput(onPixel) {
                var last = -1
                detectDragGestures(
                    onDragStart = { offset ->
                        indexAt(offset, size)?.let { last = it; onPixel(it) }
                    },
                    onDrag = { change, _ ->
                        indexAt(change.position, size)?.takeIf { it != last }?.let {
                            last = it
                            onPixel(it)
                        }
                        change.consume()
                    },
                )
            }
    }

    Canvas(canvasModifier) { drawMatrix(pixels) }
}

private fun DrawScope.drawMatrix(pixels: IntArray) {
    val side = min(size.width, size.height)
    val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
    val cell = side / MATRIX_SIZE
    drawCircle(Color(0xFF050505), side * .49f, center)
    for (index in PHONE_4A_PRO_MASK.indices) {
        if (!PHONE_4A_PRO_MASK[index]) continue
        val x = index % MATRIX_SIZE
        val y = index / MATRIX_SIZE
        val intensity = pixels.getOrElse(index) { 0 }.coerceIn(0, 255)
        val value = if (intensity == 0) 27 else intensity
        val color = Color(value, value, value)
        val gap = cell * .13f
        drawRoundRect(
            color = color,
            topLeft = Offset(origin.x + x * cell + gap, origin.y + y * cell + gap),
            size = Size(cell - gap * 2, cell - gap * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * .08f),
        )
    }
}
