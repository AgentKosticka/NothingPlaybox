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
