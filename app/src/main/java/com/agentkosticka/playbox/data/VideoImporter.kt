package com.agentkosticka.playbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.agentkosticka.playbox.model.MAX_EFFECT_FRAMES
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
    private const val MAX_DECODE_EDGE = 256
    private const val NEARBY_RETRY_OFFSET_US = 33_000L

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
            val sampleDurations = videoSampleDurations(duration, FRAME_DURATION_MS, MAX_EFFECT_FRAMES)
            val sampleTimesUs = videoSampleTimesUs(sampleDurations, duration)
            val sampleCount = sampleDurations.size
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toFloatOrNull() ?: 0f
            val decodeSize = scaledDecodeSize(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
            )
            val accumulator = VideoFrameAccumulator(FRAME_DURATION_MS)

            repeat(sampleCount) { index ->
                currentCoroutineContext().ensureActive()
                val sampleDurationMs = sampleDurations[index]
                val decoded = retriever.decodeFrame(sampleTimesUs[index], decodeSize, duration)
                if (decoded == null) {
                    accumulator.addMissing(sampleDurationMs)
                } else {
                    val oriented = try {
                        decoded.rotated(rotation)
                    } catch (error: Throwable) {
                        decoded.recycle()
                        throw error
                    }
                    try {
                        accumulator.addDecoded(ImageImporter.bitmapToPixels(oriented), sampleDurationMs)
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

    private fun MediaMetadataRetriever.decodeFrame(timeUs: Long, size: DecodeSize?, sourceDurationMs: Long): Bitmap? {
        val options = intArrayOf(
            MediaMetadataRetriever.OPTION_CLOSEST,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            MediaMetadataRetriever.OPTION_PREVIOUS_SYNC,
            MediaMetadataRetriever.OPTION_NEXT_SYNC,
        )
        decodeFrameAt(timeUs, size, options)?.let { return it }

        val nearbyOptions = intArrayOf(
            MediaMetadataRetriever.OPTION_CLOSEST,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        )
        for (candidateTimeUs in videoNearbyDecodeTimesUs(timeUs, sourceDurationMs, NEARBY_RETRY_OFFSET_US)) {
            decodeFrameAt(candidateTimeUs, size, nearbyOptions)?.let { return it }
        }
        return null
    }

    private fun MediaMetadataRetriever.decodeFrameAt(timeUs: Long, size: DecodeSize?, options: IntArray): Bitmap? {
        if (size != null) {
            for (option in options) {
                decodeRuntimeFailure { getScaledFrameAtTime(timeUs, option, size.width, size.height) }
                    ?.let { return it }
            }
        }
        for (option in options) {
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

internal fun videoSampleDurations(durationMs: Long, sampleDurationMs: Int, maxSamples: Int): IntArray {
    require(durationMs > 0)
    require(sampleDurationMs >= 33)
    require(maxSamples > 0)

    val boundedDuration = durationMs.coerceAtMost(sampleDurationMs.toLong() * maxSamples)
    val sampleCount = ceil(boundedDuration / sampleDurationMs.toDouble()).toInt().coerceIn(1, maxSamples)
    if (sampleCount == 1) return intArrayOf(boundedDuration.toInt().coerceIn(33, sampleDurationMs))

    val durations = IntArray(sampleCount) { sampleDurationMs }
    val tail = (boundedDuration % sampleDurationMs).toInt()
    if (tail == 0) return durations

    if (tail >= 33) {
        durations[durations.lastIndex] = tail
    } else {
        val borrowed = 33 - tail
        durations[durations.lastIndex - 1] -= borrowed
        durations[durations.lastIndex] = 33
    }
    return durations
}

internal fun videoSampleTimesUs(sampleDurationsMs: IntArray, sourceDurationMs: Long): LongArray {
    require(sampleDurationsMs.isNotEmpty())
    require(sampleDurationsMs.all { it >= 33 })
    require(sourceDurationMs > 0)

    val lastValidUs = (sourceDurationMs * 1_000L - 1L).coerceAtLeast(0L)
    var elapsedMs = 0L
    return LongArray(sampleDurationsMs.size) { index ->
        val durationMs = sampleDurationsMs[index]
        val midpointUs = (elapsedMs * 1_000L) + (durationMs * 1_000L / 2L)
        elapsedMs += durationMs
        midpointUs.coerceAtMost(lastValidUs)
    }
}

internal fun videoNearbyDecodeTimesUs(timeUs: Long, sourceDurationMs: Long, offsetUs: Long): LongArray {
    require(timeUs >= 0)
    require(sourceDurationMs > 0)
    require(offsetUs > 0)

    val lastValidUs = (sourceDurationMs * 1_000L - 1L).coerceAtLeast(0L)
    val candidates = linkedSetOf<Long>()
    val before = (timeUs - offsetUs).coerceAtLeast(0L)
    val after = (timeUs + offsetUs).coerceAtMost(lastValidUs)
    if (before != timeUs) candidates += before
    if (after != timeUs) candidates += after
    return candidates.toLongArray()
}
