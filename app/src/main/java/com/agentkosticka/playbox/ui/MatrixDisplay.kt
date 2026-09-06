package com.agentkosticka.playbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import kotlin.math.min

@Composable
fun MatrixDisplay(
    pixels: IntArray,
    modifier: Modifier = Modifier,
    onPixel: ((Int) -> Unit)? = null,
    onStrokeStart: (() -> Unit)? = null,
    onStrokeEnd: (() -> Unit)? = null,
) {
    fun indexAt(offset: Offset, size: IntSize): Int? {
        val side = min(size.width, size.height).toFloat()
        val originX = (size.width - side) / 2f
        val originY = (size.height - side) / 2f
        val localX = offset.x - originX
        val localY = offset.y - originY
        if (localX < 0f || localY < 0f || localX >= side || localY >= side) return null
        val cell = side / MATRIX_SIZE
        val x = (localX / cell).toInt()
        val y = (localY / cell).toInt()
        return (y * MATRIX_SIZE + x).takeIf { PHONE_4A_PRO_MASK[it] }
    }

    val activeIndices = remember { PHONE_4A_PRO_MASK.indices.filter { PHONE_4A_PRO_MASK[it] } }
    var accessibilityPosition by remember { mutableIntStateOf(activeIndices.indexOf(MATRIX_SIZE * 6 + 6).coerceAtLeast(0)) }
    val currentOnPixel = rememberUpdatedState(onPixel)
    val currentOnStrokeStart = rememberUpdatedState(onStrokeStart)
    val currentOnStrokeEnd = rememberUpdatedState(onStrokeEnd)
    val currentPixels = rememberUpdatedState(pixels)
    val matrixDescription = if (onPixel == null) {
        "13 by 13 Glyph Matrix preview"
    } else {
        "Editable Glyph Matrix. Use accessibility actions to move between active LEDs and edit the selected cell."
    }
    var canvasModifier = modifier.semantics {
        contentDescription = matrixDescription
        if (onPixel != null && activeIndices.isNotEmpty()) {
            val selected = activeIndices[accessibilityPosition.coerceIn(activeIndices.indices)]
            val row = selected / MATRIX_SIZE + 1
            val column = selected % MATRIX_SIZE + 1
            val intensity = currentPixels.value.getOrElse(selected) { 0 }.coerceIn(0, 255)
            stateDescription = "Selected row $row, column $column, intensity $intensity of 255"
            onClick("Edit selected LED") {
                currentOnStrokeStart.value?.invoke()
                currentOnPixel.value?.invoke(selected)
                currentOnStrokeEnd.value?.invoke()
                true
            }
            customActions = listOf(
                CustomAccessibilityAction("Previous active LED") {
                    accessibilityPosition = if (accessibilityPosition <= 0) activeIndices.lastIndex else accessibilityPosition - 1
                    true
                },
                CustomAccessibilityAction("Edit selected LED") {
                    currentOnStrokeStart.value?.invoke()
                    currentOnPixel.value?.invoke(selected)
                    currentOnStrokeEnd.value?.invoke()
                    true
                },
                CustomAccessibilityAction("Next active LED") {
                    accessibilityPosition = if (accessibilityPosition >= activeIndices.lastIndex) 0 else accessibilityPosition + 1
                    true
                },
            )
        }
    }
    if (onPixel != null) {
        canvasModifier = canvasModifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val index = indexAt(offset, size) ?: return@detectTapGestures
                    currentOnStrokeStart.value?.invoke()
                    currentOnPixel.value?.invoke(index)
                    currentOnStrokeEnd.value?.invoke()
                }
            }
            .pointerInput(Unit) {
                var last = -1
                detectDragGestures(
                    onDragStart = { offset ->
                        last = indexAt(offset, size) ?: -1
                        if (last >= 0) {
                            currentOnStrokeStart.value?.invoke()
                            currentOnPixel.value?.invoke(last)
                        }
                    },
                    onDragEnd = {
                        if (last >= 0) currentOnStrokeEnd.value?.invoke()
                        last = -1
                    },
                    onDragCancel = {
                        if (last >= 0) currentOnStrokeEnd.value?.invoke()
                        last = -1
                    },
                    onDrag = { change, _ ->
                        val current = indexAt(change.position, size)
                        if (current == null) {
                            if (last >= 0) currentOnStrokeEnd.value?.invoke()
                            last = -1
                        } else if (current != last) {
                            if (last >= 0) {
                                matrixLineIndices(last, current).forEach { index ->
                                    if (index != last) currentOnPixel.value?.invoke(index)
                                }
                            } else {
                                currentOnStrokeStart.value?.invoke()
                                currentOnPixel.value?.invoke(current)
                            }
                            last = current
                        }
                        change.consume()
                    },
                )
            }
    }

    Canvas(canvasModifier) { drawMatrix(pixels) }
}

/** UI-only transfer curve: keeps off cells visible while remaining monotonic from 0..255. */
internal fun previewLuminance(intensity: Int): Float {
    val normalized = intensity.coerceIn(0, 255) / 255f
    val floor = 27f / 255f
    return floor + normalized * (1f - floor)
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
        val luminance = previewLuminance(pixels.getOrElse(index) { 0 })
        val color = Color(luminance, luminance, luminance)
        val gap = cell * .13f
        drawRoundRect(
            color = color,
            topLeft = Offset(origin.x + x * cell + gap, origin.y + y * cell + gap),
            size = Size(cell - gap * 2, cell - gap * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * .08f),
        )
    }
}
