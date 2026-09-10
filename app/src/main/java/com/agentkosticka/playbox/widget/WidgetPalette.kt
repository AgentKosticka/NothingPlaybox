package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/** Semantic widget colors sourced from Android's OEM/runtime-overridable system color roles. */
data class WidgetPalette(
    val background: Int,
    val foreground: Int,
    val muted: Int,
    val inactive: Int,
    val container: Int,
    val accent: Int,
) {
    companion object {
        fun resolve(context: Context, night: Boolean? = null): WidgetPalette {
            val resolvedNight = night ?: context.resources.configuration.isNightMode
            return fromSystemColors(resolvedNight, context::getColor)
        }

        /** Used by in-app bitmap previews when only system resources are available to the renderer. */
        fun current(): WidgetPalette {
            val resources = Resources.getSystem()
            return fromSystemColors(resources.configuration.isNightMode) { resourceId ->
                resources.getColor(resourceId, null)
            }
        }

        private fun fromSystemColors(night: Boolean, color: (Int) -> Int): WidgetPalette = WidgetPalette(
            background = color(if (night) android.R.color.system_surface_container_dark else android.R.color.system_surface_container_light),
            foreground = color(if (night) android.R.color.system_on_surface_dark else android.R.color.system_on_surface_light),
            muted = color(if (night) android.R.color.system_on_surface_variant_dark else android.R.color.system_on_surface_variant_light),
            inactive = color(if (night) android.R.color.system_outline_variant_dark else android.R.color.system_outline_variant_light),
            container = color(if (night) android.R.color.system_surface_container_high_dark else android.R.color.system_surface_container_high_light),
            accent = color(if (night) android.R.color.system_primary_dark else android.R.color.system_primary_light),
        )
    }
}

private val Configuration.isNightMode: Boolean
    get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * Supplies both day and night renderings in one RemoteViews payload. The launcher selects the
 * matching bitmap immediately when UI mode changes, instead of waiting for the next widget refresh.
 */
internal fun RemoteViews.setThemedWidgetBitmap(
    context: Context,
    viewId: Int,
    render: (WidgetPalette) -> Bitmap,
) {
    setDayNightWidgetBitmaps(
        viewId,
        render(WidgetPalette.resolve(context, night = false)),
        render(WidgetPalette.resolve(context, night = true)),
    )
}

/**
 * Compatibility path for utility renderers that still draw a neutral source bitmap. The source
 * colors are discovered from the bitmap itself and remapped onto Android's semantic system roles;
 * no Nothing/widget RGB values are encoded here.
 */
internal fun RemoteViews.setThemedWidgetBitmap(
    context: Context,
    viewId: Int,
    source: Bitmap,
) {
    val notNight = source.remapTo(WidgetPalette.resolve(context, night = false))
    val night = source.remapTo(WidgetPalette.resolve(context, night = true))
    source.recycle()
    setDayNightWidgetBitmaps(viewId, notNight, night)
}

private fun RemoteViews.setDayNightWidgetBitmaps(viewId: Int, notNight: Bitmap, night: Bitmap) {
    setIcon(
        viewId,
        "setImageIcon",
        Icon.createWithBitmap(notNight),
        Icon.createWithBitmap(night),
    )
}

private fun Bitmap.remapTo(palette: WidgetPalette): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    // The most common opaque source color is the renderer's card surface.
    val counts = HashMap<Int, Int>()
    pixels.forEach { pixel ->
        if (Color.alpha(pixel) == 255) counts[pixel] = (counts[pixel] ?: 0) + 1
    }
    val sourceBackground = counts.maxByOrNull { it.value }?.key ?: Color.TRANSPARENT
    val backgroundLevel = sourceBackground.neutralLevel()

    var brightestNeutral = backgroundLevel + 1f
    var strongestChroma = 1f
    pixels.forEach { pixel ->
        if (Color.alpha(pixel) == 0) return@forEach
        val chroma = pixel.chroma()
        if (chroma <= 4f) {
            brightestNeutral = maxOf(brightestNeutral, pixel.neutralLevel())
        } else {
            strongestChroma = maxOf(strongestChroma, chroma)
        }
    }
    val neutralRange = (brightestNeutral - backgroundLevel).coerceAtLeast(1f)

    pixels.indices.forEach { index ->
        val source = pixels[index]
        val alpha = Color.alpha(source)
        if (alpha == 0) return@forEach

        val chroma = source.chroma()
        val mapped = if (chroma > 4f) {
            // Anti-aliased accent pixels are blends of the source surface and source accent.
            blend(palette.background, palette.accent, (chroma / strongestChroma).coerceIn(0f, 1f))
        } else {
            val amount = ((source.neutralLevel() - backgroundLevel) / neutralRange).coerceIn(0f, 1f)
            semanticNeutral(amount, palette)
        }
        pixels[index] = Color.argb(alpha, Color.red(mapped), Color.green(mapped), Color.blue(mapped))
    }

    return createBitmap(width, height).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun Int.neutralLevel(): Float = (Color.red(this) + Color.green(this) + Color.blue(this)) / 3f

private fun Int.chroma(): Float {
    val red = Color.red(this)
    val green = Color.green(this)
    val blue = Color.blue(this)
    return (maxOf(red, green, blue) - minOf(red, green, blue)).toFloat()
}

private fun semanticNeutral(amount: Float, palette: WidgetPalette): Int = when {
    amount <= 0.08f -> blend(palette.background, palette.container, amount / 0.08f)
    amount <= 0.22f -> blend(palette.container, palette.inactive, (amount - 0.08f) / 0.14f)
    amount <= 0.78f -> blend(palette.inactive, palette.muted, (amount - 0.22f) / 0.56f)
    else -> blend(palette.muted, palette.foreground, (amount - 0.78f) / 0.22f)
}

private fun blend(from: Int, to: Int, amount: Float): Int {
    val fraction = amount.coerceIn(0f, 1f)
    fun channel(start: Int, end: Int): Int = (start + (end - start) * fraction).roundToInt()
    return Color.rgb(
        channel(Color.red(from), Color.red(to)),
        channel(Color.green(from), Color.green(to)),
        channel(Color.blue(from), Color.blue(to)),
    )
}
