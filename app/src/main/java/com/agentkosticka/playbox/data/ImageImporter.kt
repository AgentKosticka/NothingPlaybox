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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class ImageDecodeSize(val width: Int, val height: Int)

internal fun boundedImageDecodeSize(width: Int, height: Int, maxEdge: Int = 1_024): ImageDecodeSize? {
    require(width > 0 && height > 0 && maxEdge > 0)
    val longestEdge = maxOf(width, height)
    if (longestEdge <= maxEdge) return null
    val scale = maxEdge.toDouble() / longestEdge
    return ImageDecodeSize(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
    )
}

internal fun colorToMatrixIntensity(color: Int): Int {
    val alpha = ((color ushr 24) and 0xff) / 255.0
    if (alpha == 0.0) return 0
    val r = ((color ushr 16) and 0xff) / 255.0
    val g = ((color ushr 8) and 0xff) / 255.0
    val b = (color and 0xff) / 255.0
    fun linear(value: Double) = if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
    val luminance = alpha * (.2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b))
    return (luminance.pow(1.0 / 2.2) * 255).toInt().coerceIn(0, 255)
}

object ImageImporter {
    suspend fun import(resolver: ContentResolver, uris: List<Uri>): PlayboxEffect = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty())
        require(uris.size <= 100) { "Choose at most 100 images" }
        val frameDuration = if (uris.size == 1) 120 else 180
        val frames = buildList {
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                val source = ImageDecoder.createSource(resolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    boundedImageDecodeSize(info.size.width, info.size.height)?.let { target ->
                        decoder.setTargetSize(target.width, target.height)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                try {
                    currentCoroutineContext().ensureActive()
                    add(EffectFrame(bitmapToPixels(bitmap), frameDuration))
                } finally {
                    bitmap.recycle()
                }
            }
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
                if (!PHONE_4A_PRO_MASK[index]) 0
                else colorToMatrixIntensity(scaled[index % MATRIX_SIZE, index / MATRIX_SIZE])
            }
        } finally {
            if (scaled !== square) scaled.recycle()
            if (square !== source) square.recycle()
        }
    }
}
