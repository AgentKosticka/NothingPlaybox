package com.agentkosticka.playbox.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.agentkosticka.playbox.MainActivity
import com.agentkosticka.playbox.R

enum class WidgetCategory(@param:StringRes val titleRes: Int) {
    TIME(R.string.widget_category_time),
    BATTERY(R.string.widget_category_battery),
    CALENDAR(R.string.widget_category_calendar),
    DEVICE(R.string.widget_category_device),
    PRODUCTIVITY(R.string.widget_category_productivity),
}

enum class WidgetDestination(val key: String, val category: WidgetCategory, val provider: Class<*>) {
    TIME_BARS("time-bars", WidgetCategory.TIME, TimeBarsWidget::class.java),
    DAY_DIAL("day-dial", WidgetCategory.TIME, DayDialWidget::class.java),
    CLOCK("ndot-clock", WidgetCategory.TIME, NDotClockWidget::class.java),
    DUAL_CLOCK("dual-clock", WidgetCategory.TIME, DualClockWidget::class.java),
    ALARM("next-alarm", WidgetCategory.TIME, NextAlarmWidget::class.java),
    MILESTONE("milestone", WidgetCategory.TIME, MilestoneWidget::class.java),
    BATTERY_GLYPH("battery-glyph", WidgetCategory.BATTERY, BatteryGlyphWidget::class.java),
    BATTERY_DOTS("battery-dots", WidgetCategory.BATTERY, BatteryDotsWidget::class.java),
    BATTERY_COLUMN("battery-column", WidgetCategory.BATTERY, BatteryColumnWidget::class.java),
    MONTH("month-matrix", WidgetCategory.CALENDAR, MonthMatrixWidget::class.java),
    WEEK("week-strip", WidgetCategory.CALENDAR, WeekStripWidget::class.java),
    WEEK_COLUMN("week-column", WidgetCategory.CALENDAR, WeekColumnWidget::class.java),
    AGENDA("agenda", WidgetCategory.CALENDAR, AgendaWidget::class.java),
    YEAR("year-dots", WidgetCategory.CALENDAR, YearDotsWidget::class.java),
    STORAGE("storage-matrix", WidgetCategory.DEVICE, StorageMatrixWidget::class.java),
    DEVICE("device-panel", WidgetCategory.DEVICE, DevicePanelWidget::class.java),
    SHORTCUTS("playbox-shortcuts", WidgetCategory.DEVICE, PlayboxShortcutsWidget::class.java),
    QUICK_TASKS("quick-tasks", WidgetCategory.PRODUCTIVITY, QuickTasksWidget::class.java),
    HABIT_TRACKER("habit-tracker", WidgetCategory.PRODUCTIVITY, HabitTrackerWidget::class.java);

    companion object {
        fun fromKey(key: String?): WidgetDestination = entries.firstOrNull { it.key == key } ?: TIME_BARS
        fun forProvider(provider: Class<*>): WidgetDestination = entries.first { it.provider == provider }
    }
}

const val EXTRA_WIDGET_KEY = "widget_key"

internal fun widgetIntent(context: Context, destination: WidgetDestination, appWidgetId: Int? = null): Intent =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("playbox://widgets/${destination.key}/${appWidgetId ?: "defaults"}"))
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("open_widgets", true)
        .putExtra(EXTRA_WIDGET_KEY, destination.key)
        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId ?: android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)

internal fun widgetPendingIntent(context: Context, destination: WidgetDestination, appWidgetId: Int? = null): PendingIntent =
    PendingIntent.getActivity(
        context, 0, widgetIntent(context, destination, appWidgetId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
