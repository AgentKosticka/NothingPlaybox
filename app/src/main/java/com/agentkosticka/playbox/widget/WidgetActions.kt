package com.agentkosticka.playbox.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.RemoteViews
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.calendar.EventOccurrence

/** Explicit trampoline lets us fall back at tap time, including uninstalled calendar apps. */
internal fun eventPendingIntent(context: Context, widgetId: Int, event: EventOccurrence): PendingIntent =
    PendingIntent.getActivity(context, 0,
        Intent(context, WidgetActionActivity::class.java)
            .setData(Uri.parse("playbox://event/$widgetId/${event.calendarId}/${event.eventId}/${event.beginMillis}/${event.endMillis}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra("event", event.eventId).putExtra("begin", event.beginMillis).putExtra("end", event.endMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

internal fun configureDeviceActions(context: Context, views: RemoteViews, id: Int) {
    listOf(R.id.device_battery to "battery", R.id.device_storage to "storage", R.id.device_alarm to "alarm", R.id.device_day to "day").forEach { (view, action) ->
        views.setOnClickPendingIntent(view, PendingIntent.getActivity(context, 0,
            Intent(context, WidgetActionActivity::class.java).setData(Uri.parse("playbox://device/$id/$action"))
                .putExtra("action", action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
}

class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID).takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        fun open(target: Intent): Boolean = try { startActivity(target); true } catch (_: android.content.ActivityNotFoundException) { false } catch (_: SecurityException) { false }
        if (intent.data?.host == "event") {
            val begin = intent.getLongExtra("begin", 0)
            val target = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, intent.getLongExtra("event", -1)))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, intent.getLongExtra("end", 0))
            if (!open(target) && !open(Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(begin.toString()).build()))) {
                open(widgetIntent(this, WidgetDestination.AGENDA, id))
            }
        } else {
            val action = when (intent.getStringExtra("action")) {
                "battery" -> "android.intent.action.POWER_USAGE_SUMMARY"
                "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                "alarm" -> android.provider.AlarmClock.ACTION_SHOW_ALARMS
                else -> null
            }
            if (action == null || !open(Intent(action))) open(widgetIntent(this, WidgetDestination.DEVICE, id))
        }
        finish()
    }
}
