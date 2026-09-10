package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.ui.NOTHING_RED_ARGB
import kotlin.math.roundToInt

/** Semantic widget colors resolved from Android's device theme instead of fixed swatches. */
data class WidgetPalette(
    val background: Int,
    val foreground: Int,
    val muted: Int,
    val inactive: Int,
    val container: Int,
    val accent: Int,
) {
    companion object {
        /** Only used when no Android themed context is available, such as renderer-only tests. */
        val fallbackDark = WidgetPalette(
            background = 0xFF1B1B1D.toInt(),
            foreground = Color.WHITE,
            muted = 0xFFBDBDBD.toInt(),
            inactive = 0xFF444446.toInt(),
            container = 0xFF303033.toInt(),
            accent = NOTHING_RED_ARGB,
        )

        fun resolve(context: Context, night: Boolean? = null): WidgetPalette {
            val sourceConfiguration = context.resources.configuration
            val resolvedNight = night ?: (
                sourceConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            )
            val themedContext = if (night == null) {
                ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
            } else {
                val configuration = Configuration(sourceConfiguration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (resolvedNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                ContextThemeWrapper(
                    context.createConfigurationContext(configuration),
                    android.R.style.Theme_DeviceDefault_DayNight,
                )
            }

            fun themeColor(attribute: Int, fallback: Int): Int {
                val values = themedContext.obtainStyledAttributes(intArrayOf(attribute))
                return try {
                    values.getColor(0, fallback)
                } finally {
                    values.recycle()
                }
            }

            val fallbackBackground = if (resolvedNight) fallbackDark.background else Color.WHITE
            val fallbackForeground = if (resolvedNight) Color.WHITE else Color.BLACK
            val background = themeColor(android.R.attr.colorBackground, fallbackBackground)
            val foreground = themeColor(android.R.attr.textColorPrimary, fallbackForeground)
            val muted = themeColor(
                android.R.attr.textColorSecondary,
                ColorUtils.blendARGB(background, foreground, 0.65f),
            )
            val accent = themeColor(android.R.attr.colorAccent, NOTHING_RED_ARGB)

            return WidgetPalette(
                background = background,
                foreground = foreground,
                muted = muted,
                inactive = ColorUtils.blendARGB(background, foreground, 0.18f),
                container = ColorUtils.blendARGB(background, foreground, 0.08f),
                accent = accent,
            )
        }
    }
}

/**
 * Supplies both day and night renderings in one RemoteViews payload. The launcher selects the
 * matching bitmap when UI mode changes, so widgets do not have to wait for the next refresh.
 */
internal fun RemoteViews.setThemedWidgetBitmap(
    context: Context,
    viewId: Int,
    render: (WidgetPalette) -> Bitmap,
) {
    setDayNightBitmaps(
        viewId,
        render(WidgetPalette.resolve(context, night = false)),
        render(WidgetPalette.resolve(context, night = true)),
    )
}

/**
 * Transitional adapter for the older utility renderer. It treats its fixed grayscale/red output as
 * semantic paint slots, then replaces those slots with the device palette before the widget leaves
 * the app process. This keeps production widget colors device-driven without duplicating that large
 * renderer while it is being migrated to semantic colors.
 */
internal fun RemoteViews.setThemedWidgetBitmap(
    context: Context,
    viewId: Int,
    legacyBitmap: Bitmap,
) {
    val notNight = legacyBitmap.recolorLegacyWidget(WidgetPalette.resolve(context, night = false))
    val night = legacyBitmap.recolorLegacyWidget(WidgetPalette.resolve(context, night = true))
    legacyBitmap.recycle()
    setDayNightBitmaps(viewId, notNight, night)
}

private fun RemoteViews.setDayNightBitmaps(viewId: Int, notNight: Bitmap, night: Bitmap) {
    setIcon(
        viewId,
        "setImageIcon",
        Icon.createWithBitmap(notNight),
        Icon.createWithBitmap(night),
    )
}

private fun Bitmap.recolorLegacyWidget(palette: WidgetPalette): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    val sourceBackground = intArrayOf(17, 17, 17)
    val sourceAccent = intArrayOf(
        Color.red(NOTHING_RED_ARGB),
        Color.green(NOTHING_RED_ARGB),
        Color.blue(NOTHING_RED_ARGB),
    )
    val accentVector = intArrayOf(
        sourceAccent[0] - sourceBackground[0],
        sourceAccent[1] - sourceBackground[1],
        sourceAccent[2] - sourceBackground[2],
    )
    val accentLengthSquared = accentVector.sumOf { it * it }.coerceAtLeast(1)

    pixels.indices.forEach { index ->
        val source = pixels[index]
        val alpha = Color.alpha(source)
        if (alpha == 0) return@forEach

        val red = Color.red(source)
        val green = Color.green(source)
        val blue = Color.blue(source)
        val chroma = red - maxOf(green, blue)
        val mapped = if (chroma > 8) {
            val projection = (
                (red - sourceBackground[0]) * accentVector[0] +
                    (green - sourceBackground[1]) * accentVector[1] +
                    (blue - sourceBackground[2]) * accentVector[2]
                ).toFloat() / accentLengthSquared
            blend(palette.background, palette.accent, projection.coerceIn(0f, 1f))
        } else {
            val gray = (red + green + blue) / 3f
            mapLegacyNeutral(gray, palette)
        }
        pixels[index] = Color.argb(
            alpha,
            Color.red(mapped),
            Color.green(mapped),
            Color.blue(mapped),
        )
    }

    return createBitmap(width, height).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun mapLegacyNeutral(gray: Float, palette: WidgetPalette): Int = when {
    gray <= 17f -> palette.background
    gray <= 36f -> blend(palette.background, palette.container, (gray - 17f) / 19f)
    gray <= 57f -> blend(palette.container, palette.inactive, (gray - 36f) / 21f)
    gray <= 211f -> blend(palette.inactive, palette.muted, (gray - 57f) / 154f)
    else -> blend(palette.muted, palette.foreground, (gray - 211f) / 44f)
}

private fun blend(from: Int, to: Int, fraction: Float): Int {
    val amount = fraction.coerceIn(0f, 1f)
    fun channel(start: Int, end: Int): Int = (start + (end - start) * amount).roundToInt()
    return Color.rgb(
        channel(Color.red(from), Color.red(to)),
        channel(Color.green(from), Color.green(to)),
        channel(Color.blue(from), Color.blue(to)),
    )
}
