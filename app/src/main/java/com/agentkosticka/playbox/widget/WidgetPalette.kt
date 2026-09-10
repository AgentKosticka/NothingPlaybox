package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.widget.RemoteViews

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

        fun resolve(context: Context, night: Boolean? = null): WidgetPalette {
            val resolvedNight = night ?: context.resources.configuration.isNightMode
            return if (resolvedNight) DARK else LIGHT
        }

        /** Used by in-app bitmap previews when only system configuration is available. */
        fun current(): WidgetPalette =
            if (Resources.getSystem().configuration.isNightMode) DARK else LIGHT
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
    setIcon(
        viewId,
        "setImageIcon",
        Icon.createWithBitmap(render(WidgetPalette.resolve(context, night = false))),
        Icon.createWithBitmap(render(WidgetPalette.resolve(context, night = true))),
    )
}
