package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.calendar.CalendarDataState
import com.agentkosticka.playbox.calendar.CalendarRepository
import com.agentkosticka.playbox.calendar.EventOccurrence
import com.agentkosticka.playbox.calendar.agendaEvents
import com.agentkosticka.playbox.calendar.selectedCalendarIds
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class NextEventWidget : InstanceWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetInstanceSettings(context).load(it) }
        TimeBarsWidget.requestImmediateUpdate(context)
        TimeBarsWidget.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        TimeBarsWidget.requestImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) { TimeBarsWidget.cancelIfUnused(context) }

    companion object {
        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()
        private fun ids(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NextEventWidget::class.java))

        fun updateAll(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
            val configs = ids(context).associateWith { WidgetInstanceSettings(context).load(it).agenda }
            if (configs.isEmpty()) return
            val start = now.toInstant().minus(Duration.ofDays(1)).toEpochMilli()
            val end = now.toInstant().plus(Duration.ofDays(8)).toEpochMilli()
            val data = CalendarRepository(context).read(start, end, configs.values.map { it.calendarIds })
            val manager = AppWidgetManager.getInstance(context)
            configs.forEach { (id, settings) -> manager.updateAppWidget(id, views(context, id, settings, data, now)) }
        }

        private fun views(context: Context, id: Int, settings: AgendaSettings, state: CalendarDataState, now: ZonedDateTime): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_event)
            val edit = widgetPendingIntent(context, WidgetDestination.NEXT_EVENT, id)
            views.setOnClickPendingIntent(R.id.next_event_root, edit)
            views.setOnClickPendingIntent(R.id.next_event_empty, edit)
            val data = state as? CalendarDataState.Available
            val selected = data?.let { selectedCalendarIds(settings.calendarIds, it.calendars) }.orEmpty()
            val event = data?.let { agendaEvents(it.events, selected, settings.showAllDay, now, 1).firstOrNull() }
            if (event == null) {
                views.setViewVisibility(R.id.next_event_content, View.GONE)
                views.setViewVisibility(R.id.next_event_empty, View.VISIBLE)
                views.setTextViewText(R.id.next_event_empty, context.getString(when (state) {
                    CalendarDataState.PermissionMissing -> R.string.agenda_permission_off
                    CalendarDataState.Error -> R.string.agenda_error
                    is CalendarDataState.Available -> if (selected.isEmpty()) R.string.agenda_select_calendars else R.string.agenda_no_events
                }))
            } else {
                views.setViewVisibility(R.id.next_event_empty, View.GONE)
                views.setViewVisibility(R.id.next_event_content, View.VISIBLE)
                bindEvent(context, views, id, event, now)
            }
            return views
        }

        private fun bindEvent(context: Context, views: RemoteViews, id: Int, event: EventOccurrence, now: ZonedDateTime) {
            val start = Instant.ofEpochMilli(event.beginMillis).atZone(now.zone)
            val end = Instant.ofEpochMilli(event.endMillis).atZone(now.zone)
            val ongoing = !event.allDay && !now.isBefore(start) && now.isBefore(end)
            val label = if (ongoing) context.getString(R.string.next_event_now) else context.getString(R.string.next_event_next)
            val title = event.title.ifBlank { context.getString(R.string.agenda_untitled) }
            val timing = when {
                event.allDay -> context.getString(R.string.agenda_all_day)
                ongoing -> context.getString(R.string.next_event_ends_in, compactDuration(Duration.between(now, end)))
                else -> context.getString(R.string.next_event_starts_in, compactDuration(Duration.between(now, start)))
            }
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            val clock = if (event.allDay) event.startDate(now.zone).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                else start.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
            views.setTextViewText(R.id.next_event_label, label)
            views.setTextViewText(R.id.next_event_title, title)
            views.setTextViewText(R.id.next_event_time, "$clock · $timing")
            views.setContentDescription(R.id.next_event_content, "$label, $title, $clock, $timing")
            views.setOnClickPendingIntent(R.id.next_event_content, eventPendingIntent(context, id, event))
        }

        internal fun compactDuration(duration: Duration): String {
            val minutes = duration.toMinutes().coerceAtLeast(0)
            return when {
                minutes < 60 -> "${minutes}m"
                minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m"
                else -> "${minutes / (24 * 60)}d ${(minutes % (24 * 60)) / 60}h"
            }
        }
    }
}
