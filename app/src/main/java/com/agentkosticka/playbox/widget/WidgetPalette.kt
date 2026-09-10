package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.widget.RemoteViews

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
    setIcon(
        viewId,
        "setImageIcon",
        Icon.createWithBitmap(render(WidgetPalette.resolve(context, night = false))),
        Icon.createWithBitmap(render(WidgetPalette.resolve(context, night = true))),
    )
}
