package com.agentkosticka.playbox.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

/** Reading text stays quiet and legible; dot lettering is reserved for display numerals. */
internal object WidgetTypography {
    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val label: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun number(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int,
        maxWidth: Float, align: Paint.Align = Paint.Align.LEFT, face: Typeface) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face }
        val fitted = size * minOf(1f, maxWidth / paint.measureText(value).coerceAtLeast(1f))
        text(canvas, value, x, baseline, fitted, color, maxWidth, align, face)
    }

    fun text(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int,
        maxWidth: Float, align: Paint.Align = Paint.Align.LEFT, face: Typeface = body) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = face
            textAlign = align
        }
        canvas.drawText(TextUtils.ellipsize(value, paint, maxWidth.coerceAtLeast(1f), TextUtils.TruncateAt.END).toString(), x, baseline, paint)
    }
}
