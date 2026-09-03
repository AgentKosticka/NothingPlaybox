package com.agentkosticka.playbox.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.get
import androidx.core.graphics.scale
import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.pow

object ImageImporter {
    suspend fun import(resolver: ContentResolver, uris: List<Uri>): PlayboxEffect = withContext(Dispatchers.Default) {
        require(uris.isNotEmpty())
        require(uris.size <= 100) { "Choose at most 100 images" }
        val frames = uris.map { uri ->
            val source = ImageDecoder.createSource(resolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = maxOf(info.size.width, info.size.height) / 1024f
                if (scale > 1f) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val pixels = bitmapToPixels(bitmap)
            bitmap.recycle()
            EffectFrame(pixels, if (uris.size == 1) 120 else 180)
        }
        PlayboxEffect(
            id = UUID.randomUUID().toString(),
            name = if (uris.size == 1) "Imported image" else "Imported animation",
            description = "Created from ${uris.size} image${if (uris.size == 1) "" else "s"}",
            frames = frames,
        )
    }

    fun bitmapToPixels(source: Bitmap): IntArray {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = square.scale(MATRIX_SIZE, MATRIX_SIZE)
        return try {
            IntArray(PIXEL_COUNT) { index ->
                if (!PHONE_4A_PRO_MASK[index]) 0 else {
                    val color = scaled[index % MATRIX_SIZE, index / MATRIX_SIZE]
                    val r = ((color shr 16) and 0xff) / 255.0
                    val g = ((color shr 8) and 0xff) / 255.0
                    val b = (color and 0xff) / 255.0
                    fun linear(value: Double) = if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
                    val luminance = .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b)
                    (luminance.pow(1.0 / 2.2) * 255).toInt().coerceIn(0, 255)
                }
            }
        } finally {
            if (scaled !== square) scaled.recycle()
            if (square !== source) square.recycle()
        }
    }
}
