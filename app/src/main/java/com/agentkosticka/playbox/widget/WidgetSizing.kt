package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.RemoteViews

/** Launcher bounds describe portrait (min width/max height) and landscape (max width/min height). */
internal fun sizedWidgetViews(
    options: Bundle,
    defaultWidth: Int,
    defaultHeight: Int,
    render: (Int, Int) -> RemoteViews,
): RemoteViews {
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultWidth)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultHeight)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
    val portrait = UtilityWidgetRenderer.bitmapSize(minWidth, maxHeight)
    val landscape = UtilityWidgetRenderer.bitmapSize(maxWidth, minHeight)
    val portraitViews = render(portrait.first, portrait.second)
    if (portrait == landscape) return portraitViews
    return RemoteViews(render(landscape.first, landscape.second), portraitViews)
}

/** Account for the native control shelf before calculating bitmap geometry. */
internal fun interactiveWidgetViews(options: Bundle, controlsHeight: Int, render: (Int, Int) -> RemoteViews): RemoteViews {
    fun at(width: Float, height: Float): RemoteViews {
        val (w, h) = UtilityWidgetRenderer.bitmapSize(width.toInt().coerceAtLeast(1),
            (height.toInt() - controlsHeight).coerceAtLeast(1))
        return render(w, h)
    }
    val sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
    if (!sizes.isNullOrEmpty()) return RemoteViews(sizes.distinct().take(16).associateWith { at(it.width, it.height) })
    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).toFloat()
    val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160).toFloat()
    val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW.toInt()).toFloat()
    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH.toInt()).toFloat()
    return RemoteViews(at(maxW, minH), at(minW, maxH))
}
