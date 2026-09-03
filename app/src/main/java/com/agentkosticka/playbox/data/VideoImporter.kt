package com.agentkosticka.playbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.agentkosticka.playbox.model.PlayboxEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

object VideoImporter {
    private const val FRAME_DURATION_MS = 100
    private const val MAX_DURATION_MS = 60_000L
    private const val MAX_FRAMES = 600
    private const val MAX_DECODE_EDGE = 256

    suspend fun import(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): PlayboxEffect = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val sourceDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("This video does not report a duration")
            require(sourceDuration > 0) { "The selected video is empty" }

            val duration = sourceDuration.coerceAtMost(MAX_DURATION_MS)
            val sampleCount = ceil(duration / FRAME_DURATION_MS.toDouble()).toInt().coerceIn(1, MAX_FRAMES)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toFloatOrNull() ?: 0f
            val decodeSize = scaledDecodeSize(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
            )
            val accumulator = VideoFrameAccumulator(FRAME_DURATION_MS)

            repeat(sampleCount) { index ->
                currentCoroutineContext().ensureActive()
                val timeUs = index.toLong() * FRAME_DURATION_MS * 1_000L
                val decoded = retriever.decodeFrame(timeUs, decodeSize)
                if (decoded == null) {
                    accumulator.addMissing()
                } else {
                    val oriented = try {
                        decoded.rotated(rotation)
                    } catch (error: Throwable) {
                        decoded.recycle()
                        throw error
                    }
                    try {
                        accumulator.addDecoded(ImageImporter.bitmapToPixels(oriented))
                    } finally {
                        if (oriented !== decoded) oriented.recycle()
                        decoded.recycle()
                    }
                }
                onProgress((index + 1f) / sampleCount)
            }

            val frames = accumulator.frames
            require(frames.isNotEmpty()) { "No frames could be decoded from this video" }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?.take(60)
                ?: context.displayName(uri)
                ?: "Imported video"
            PlayboxEffect(
                id = UUID.randomUUID().toString(),
                name = title,
                description = buildString {
                    append("Video · ")
                    append(frames.size)
                    append(" matrix frames")
                    if (sourceDuration > MAX_DURATION_MS) append(" · first 60 seconds")
                },
                frames = frames,
            )
        } finally {
            retriever.release()
        }
    }

    private data class DecodeSize(val width: Int, val height: Int)

    private fun scaledDecodeSize(width: Int?, height: Int?): DecodeSize? {
        if (width == null || height == null || width <= 0 || height <= 0) return null
        val longestEdge = maxOf(width, height)
        if (longestEdge <= MAX_DECODE_EDGE) return null
        val scale = MAX_DECODE_EDGE.toDouble() / longestEdge
        return DecodeSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun MediaMetadataRetriever.decodeFrame(timeUs: Long, size: DecodeSize?): Bitmap? {
        val options = intArrayOf(
            MediaMetadataRetriever.OPTION_CLOSEST,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            MediaMetadataRetriever.OPTION_PREVIOUS_SYNC,
            MediaMetadataRetriever.OPTION_NEXT_SYNC,
        )
        for (option in options) {
            if (size != null) {
                decodeRuntimeFailure { getScaledFrameAtTime(timeUs, option, size.width, size.height) }
                    ?.let { return it }
            }
            decodeRuntimeFailure { getFrameAtTime(timeUs, option) }
                ?.let { return it }
        }
        return null
    }

    private inline fun decodeRuntimeFailure(block: () -> Bitmap?): Bitmap? = try {
        block()
    } catch (_: RuntimeException) {
        null
    }

    private fun Context.displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column < 0) return@use null
            val rawName = cursor.getString(column)?.trim().orEmpty()
            if (rawName.isEmpty()) return@use null
            rawName.substringBeforeLast('.', missingDelimiterValue = rawName)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.take(60)
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun Bitmap.rotated(degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
