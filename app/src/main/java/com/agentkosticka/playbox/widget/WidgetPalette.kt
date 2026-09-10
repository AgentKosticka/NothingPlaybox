package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.agentkosticka.playbox.ui.NOTHING_RED_ARGB

/** Semantic widget colors resolved from Android's device theme instead of fixed swatches. */
internal data class WidgetPalette(
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
    val notNight = render(WidgetPalette.resolve(context, night = false))
    val night = render(WidgetPalette.resolve(context, night = true))
    setIcon(
        viewId,
        "setImageIcon",
        Icon.createWithBitmap(notNight),
        Icon.createWithBitmap(night),
    )
}
