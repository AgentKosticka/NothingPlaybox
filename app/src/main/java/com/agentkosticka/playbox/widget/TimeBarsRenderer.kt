package com.agentkosticka.playbox.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.ui.NothingDotFont
import java.time.ZonedDateTime
import kotlin.math.floor

/** Shared by the launcher and in-app preview. A tiny original dot alphabet needs no font asset. */
object TimeBarsRenderer {
    private val dotTypeface get() = NothingDotFont.typeface
    private val dotFontAvailable get() = NothingDotFont.available

    internal fun textWidth(value: String, step: Float): Float {
        if (value.isEmpty()) return 0f
        if (!dotFontAvailable) return ((value.length - 1) * 6 + 4.72f) * step
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = dotTypeface
            textSize = step * 7.2f
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(value, 0, value.length, bounds)
        return maxOf(paint.measureText(value), bounds.right.toFloat())
    }

    internal fun labelThatFits(value: String, step: Float, availableWidth: Float = 238f): String {
        if (textWidth(value, step) <= availableWidth) return value
        for (length in value.length - 1 downTo 1) {
            val candidate = value.take(length) + "..."
            if (textWidth(candidate, step) <= availableWidth) return candidate
        }
        return "..."
    }

    internal fun dotText(canvas: Canvas, value: String, x: Float, y: Float, step: Float, color: Int = Color.WHITE) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = dotTypeface
            textSize = step * 7.2f
            isSubpixelText = true
        }
        if (dotFontAvailable) {
            canvas.drawText(value, x, y + step * 4.8f, paint)
        } else value.forEachIndexed { index, char ->
            glyphs[char]?.forEachIndexed { cell, bit ->
                if (bit == '1') canvas.drawCircle(
                    x + index * step * 6 + cell % 5 * step,
                    y + cell / 5 * step,
                    step * .36f,
                    paint,
                )
            }
        }
    }

    private val glyphs = mapOf(
        'A' to "01110100011000111111100011000110001", 'B' to "11110100011000111110100011000111110",
        'C' to "01111100001000010000100001000001111", 'D' to "11110100011000110001100011000111110",
        'E' to "11111100001000011110100001000011111", 'F' to "11111100001000011110100001000010000",
        'G' to "01111100001000010111100011000101111", 'H' to "10001100011000111111100011000110001",
        'I' to "11111001000010000100001000010011111", 'J' to "00111000100001000010000101001001100",
        'K' to "10001100101010011000101001001010001", 'L' to "10000100001000010000100001000011111",
        'M' to "10001110111010110101100011000110001", 'N' to "10001110011010110011100011000110001",
        'O' to "01110100011000110001100011000101110", 'P' to "11110100011000111110100001000010000",
        'Q' to "01110100011000110001101011001001101", 'R' to "11110100011000111110101001001010001",
        'S' to "01111100001000001110000010000111110", 'T' to "11111001000010000100001000010000100",
        'U' to "10001100011000110001100011000101110", 'V' to "10001100011000110001100010101000100",
        'W' to "10001100011000110101101011010101010", 'X' to "10001100010101000100010101000110001",
        'Y' to "10001100010101000100001000010000100", 'Z' to "11111000010001000100010001000011111",
        '0' to "01110100011001110101110011000101110", '1' to "00100011000010000100001000010001110",
        '2' to "01110100010000100010001000100011111", '3' to "11110000010000101110000010000111110",
        '4' to "00010001100101010010111110001000010", '5' to "11111100001000011110000010000111110",
        '6' to "01110100001000011110100011000101110", '7' to "11111000010001000100010000100001000",
        '8' to "01110100011000101110100011000101110", '9' to "01110100011000101111000010000101110",
        '%' to "11001110100001000100010000101110011",
        '.' to "00000000000000000000000000000000100",
    )

    fun render(
        now: ZonedDateTime,
        settings: TimeBarsSettings = TimeBarsSettings(),
        width: Int = 720,
        height: Int = 360,
        palette: WidgetPalette = WidgetPalette.fallbackDark,
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        // Use one scale for both axes so dots and letterforms stay round at every size.
        val scale = minOf(width / 720f, height / 360f)
        canvas.scale(scale, scale)
        val w = width / scale
        val h = height / scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.background
        canvas.drawRoundRect(0f, 0f, w, h, 32f, 32f, paint)
        fun text(value: String, x: Float, y: Float, step: Float, color: Int) = dotText(canvas, value, x, y, step, color)
        timeProgress(now, settings.weekStart).forEachIndexed { index, bar ->
            val rowHeight = (h - 40f) / 4f
            val y = 20f + index * rowHeight + (rowHeight - 30f) / 2f
            val textStep = 5.2f
            val label = labelThatFits(bar.label, textStep, 238f)
            text(label, 36f, y, textStep, palette.foreground)
            val percent = "${floor(bar.fraction * 100).toInt()}%"
            text(percent, w - 36f - textWidth(percent, textStep), y, textStep, palette.foreground)
            val left = 285f
            val right = w - 205f
            val columns = ((right - left) / 10f).toInt().coerceAtLeast(2) + 1
            val step = (right - left) / (columns - 1)
            val dotY = y + 6f
            val rows = 2
            val dots = filledDots(columns * rows, bar.fraction, settings.fill, index)
            dots.forEachIndexed { dot, filled ->
                paint.color = if (filled) palette.foreground else palette.inactive
                // Column-major order lets the rows fill one dot at a time.
                canvas.drawCircle(left + dot / rows * step, dotY + dot % rows * 12f,
                    3.4f, paint)
            }
        }
        return bitmap
    }
}
