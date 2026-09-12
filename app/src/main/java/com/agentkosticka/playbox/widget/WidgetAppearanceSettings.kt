package com.agentkosticka.playbox.widget

import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews
import com.agentkosticka.playbox.R

/** Global appearance preferences, independent of per-instance content and layout settings. */
class WidgetAppearanceSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("widget-appearance", Context.MODE_PRIVATE)
    var classicRed: Boolean
        get() = prefs.getBoolean("classic-red-accent", false)
        set(value) {
            prefs.edit().putBoolean("classic-red-accent", value).apply()
            InstanceWidgetProvider.refreshAll(context)
        }

    companion object { const val NOTHING_RED: Int = 0xFFD71920.toInt() }
}

/** Apply the same optional accent override to native controls as to bitmap palettes. */
internal fun widgetRemoteViews(context: Context, layout: Int): RemoteViews =
    RemoteViews(context.packageName, layout).apply {
        if (WidgetAppearanceSettings(context).classicRed) {
            val label = when (layout) {
                R.layout.widget_agenda_compact, R.layout.widget_agenda_wide -> R.id.agenda_header
                R.layout.widget_dual_clock -> R.id.dual_clock_remote_date
                R.layout.widget_next_event -> R.id.next_event_label
                R.layout.widget_playbox_shortcuts -> R.id.shortcut_aod
                else -> null
            }
            label?.let { setTextColor(it, WidgetAppearanceSettings.NOTHING_RED) }
            val button = when (layout) {
                R.layout.widget_focus_timer -> R.id.focus_start_pause
                R.layout.widget_goal_tracker -> R.id.goal_plus
                R.layout.widget_habit_tracker -> R.id.habit_tracker_toggle
                R.layout.widget_matrix_showcase -> R.id.matrix_showcase_active
                R.layout.widget_tally_counter -> R.id.tally_plus
                else -> null
            }
            button?.let {
                setColorStateList(it, "setBackgroundTintList", ColorStateList.valueOf(WidgetAppearanceSettings.NOTHING_RED))
                setTextColor(it, android.graphics.Color.WHITE)
            }
        }
    }
