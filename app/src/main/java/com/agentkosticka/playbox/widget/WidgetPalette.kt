package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.widget.RemoteViews

/** Public-Android appearance modes that harmonise with Nothing OS without private OS APIs. */
enum class WidgetVisualStyle {
    CLASSIC,
    DYNAMIC,
    GLASS,
    HIGH_CONTRAST;

    fun next(): WidgetVisualStyle = entries[(ordinal + 1) % entries.size]
}

/** Semantic widget colors backed by Nothing's classic branded light/dark widget palette. */
data class WidgetPalette(
    val background: Int,
    val foreground: Int,
    val muted: Int,
    val inactive: Int,
    val container: Int,
    val accent: Int,
) {
    companion object {
        private val LIGHT = WidgetPalette(
            background = 0xFFF1F1F1.toInt(),
            foreground = 0xFF111111.toInt(),
            muted = 0xFF5A5A5A.toInt(),
            inactive = 0xFFBDBDBD.toInt(),
            container = 0xFFFFFFFF.toInt(),
            accent = 0xFFD71920.toInt(),
        )

        private val DARK = WidgetPalette(
            background = 0xFF1B1B1B.toInt(),
            foreground = 0xFFFFFFFF.toInt(),
            muted = 0xFFBDBDBD.toInt(),
            inactive = 0xFF393939.toInt(),
            container = 0xFF242424.toInt(),
            accent = 0xFFD71920.toInt(),
        )

        private val LIGHT_CONTRAST = WidgetPalette(
            background = 0xFFFFFFFF.toInt(),
            foreground = 0xFF000000.toInt(),
            muted = 0xFF303030.toInt(),
            inactive = 0xFF9A9A9A.toInt(),
            container = 0xFFFFFFFF.toInt(),
            accent = 0xFFB00008.toInt(),
        )

        private val DARK_CONTRAST = WidgetPalette(
            background = 0xFF000000.toInt(),
            foreground = 0xFFFFFFFF.toInt(),
            muted = 0xFFD8D8D8.toInt(),
            inactive = 0xFF5A5A5A.toInt(),
            container = 0xFF000000.toInt(),
            accent = 0xFFFF4D55.toInt(),
        )

        fun resolve(
            context: Context,
            night: Boolean? = null,
            style: WidgetVisualStyle = WidgetVisualStyle.CLASSIC,
        ): WidgetPalette {
            val resolvedNight = night ?: context.resources.configuration.isNightMode
            val classic = if (resolvedNight) DARK else LIGHT
            return when (style) {
                WidgetVisualStyle.CLASSIC -> classic
                WidgetVisualStyle.HIGH_CONTRAST -> if (resolvedNight) DARK_CONTRAST else LIGHT_CONTRAST
                WidgetVisualStyle.GLASS -> classic.copy(
                    background = classic.background.withAlpha(if (resolvedNight) 0xD0 else 0xC2),
                    container = classic.container.withAlpha(if (resolvedNight) 0xB8 else 0xA8),
                )
                WidgetVisualStyle.DYNAMIC -> classic.copy(
                    // System accent colors are wallpaper-derived on modern Android. Keeping the
                    // established Playbox surface colors preserves contrast across launcher hosts.
                    accent = runCatching { context.getColor(android.R.color.system_accent1_500) }
                        .getOrDefault(classic.accent),
                    inactive = runCatching { context.getColor(android.R.color.system_neutral1_500) }
                        .getOrDefault(classic.inactive),
                )
            }
        }

        /** Used by in-app bitmap previews when only system configuration is available. */
        fun current(): WidgetPalette =
            if (Resources.getSystem().configuration.isNightMode) DARK else LIGHT
    }
}

private val Configuration.isNightMode: Boolean
    get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/**
 * Supplies both day and night renderings in one RemoteViews payload. The launcher selects the
 * matching bitmap immediately when UI mode changes, instead of waiting for the next widget refresh.
 */
internal fun RemoteViews.setThemedWidgetBitmap(
    context: Context,
    viewId: Int,
    render: (WidgetPalette) -> Bitmap,
) {
    setStyledWidgetBitmap(context, viewId, WidgetVisualStyle.CLASSIC, render)
}

/** Same as [setThemedWidgetBitmap], with an explicit Playbox appearance mode. */
internal fun RemoteViews.setStyledWidgetBitmap(
    context: Context,
    viewId: Int,
    style: WidgetVisualStyle,
    render: (WidgetPalette) -> Bitmap,
) {
    setIcon(
        viewId,
        "setImageIcon",
        Icon.createWithBitmap(render(WidgetPalette.resolve(context, night = false, style = style))),
        Icon.createWithBitmap(render(WidgetPalette.resolve(context, night = true, style = style))),
    )
}
