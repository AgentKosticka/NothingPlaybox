package com.agentkosticka.playbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.PlayboxEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.ceil

object VideoImporter {
    private const val FRAME_DURATION_MS = 100
    private const val MAX_DURATION_MS = 60_000L
    private const val MAX_FRAMES = 600

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
            val frames = mutableListOf<EffectFrame>()

            repeat(sampleCount) { index ->
                val timeUs = index.toLong() * FRAME_DURATION_MS * 1_000L
                val decoded = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (decoded != null) {
                    val oriented = decoded.rotated(rotation)
                    val pixels = ImageImporter.bitmapToPixels(oriented)
                    if (oriented !== decoded) oriented.recycle()
                    decoded.recycle()

                    val previous = frames.lastOrNull()
                    if (previous != null && previous.pixels.contentEquals(pixels) && previous.durationMs < 5_000) {
                        frames[frames.lastIndex] = previous.copy(
                            durationMs = (previous.durationMs + FRAME_DURATION_MS).coerceAtMost(5_000),
                        )
                    } else {
                        frames += EffectFrame(pixels, FRAME_DURATION_MS)
                    }
                }
                onProgress((index + 1f) / sampleCount)
            }

            require(frames.isNotEmpty()) { "No frames could be decoded from this video" }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?.take(60)
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

    private fun Bitmap.rotated(degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
