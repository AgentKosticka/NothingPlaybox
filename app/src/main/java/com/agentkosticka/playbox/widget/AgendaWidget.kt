package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.calendar.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AgendaWidget : InstanceWidgetProvider() {
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
        fun hasWidgets(context: Context) = ids(context).isNotEmpty()
        private fun ids(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AgendaWidget::class.java))

        fun updateAll(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
            val configs = ids(context).associateWith { WidgetInstanceSettings(context).load(it).agenda }
            if (configs.isEmpty()) return
            val repository = CalendarRepository(context)
            // Padding covers UTC all-day dates even in UTC+14 / UTC-12. UI filters by dates.
            val start = now.toLocalDate().minusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val end = now.toLocalDate().plusDays(9).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val data = repository.read(start, end, configs.values.map { it.calendarIds })
            val manager = AppWidgetManager.getInstance(context)
            configs.forEach { (id, config) ->
                val options = manager.getAppWidgetOptions(id)
                val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140).coerceAtLeast(80)
                val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140).coerceAtLeast(80)
                val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW).coerceAtLeast(minW)
                val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH).coerceAtLeast(minH)
                manager.updateAppWidget(id, RemoteViews(
                    agendaViews(context, id, config, data, now, maxW, minH),
                    agendaViews(context, id, config, data, now, minW, maxH),
                ))
            }
        }
    }
}

internal fun agendaViews(context: Context, id: Int, settings: AgendaSettings, state: CalendarDataState, now: ZonedDateTime, widthDp: Int, heightDp: Int): RemoteViews {
    val wide = widthDp >= 250
    val views = widgetRemoteViews(context, if (wide) R.layout.widget_agenda_wide else R.layout.widget_agenda_compact)
    val edit = widgetPendingIntent(context, WidgetDestination.AGENDA, id)
    views.setOnClickPendingIntent(R.id.agenda_root, edit)
    views.setOnClickPendingIntent(R.id.agenda_header, edit)
    views.setOnClickPendingIntent(R.id.agenda_empty, edit)
    val data = state as? CalendarDataState.Available
    val selected = data?.let { selectedCalendarIds(settings.calendarIds, it.calendars) }.orEmpty()
    val maxRows = ((heightDp - 40) / 48).coerceIn(1, 3)
    val events = data?.let { agendaEvents(it.events, selected, settings.showAllDay, now, minOf(settings.maxItems, maxRows)) }
        .orEmpty().filter { it.startDate(now.zone) < now.toLocalDate().plusDays(7) }
    val message = when (state) {
        CalendarDataState.PermissionMissing -> R.string.agenda_permission_off
        CalendarDataState.Error -> R.string.agenda_error
        is CalendarDataState.Available -> if (selected.isEmpty()) R.string.agenda_select_calendars else R.string.agenda_no_events
    }
    views.setTextViewText(R.id.agenda_empty, context.getString(message))
    views.setViewVisibility(R.id.agenda_empty, if (events.isEmpty()) View.VISIBLE else View.GONE)
    val rows = listOf(R.id.agenda_row_1, R.id.agenda_row_2, R.id.agenda_row_3)
    val times = listOf(R.id.agenda_time_1, R.id.agenda_time_2, R.id.agenda_time_3)
    val titles = listOf(R.id.agenda_title_1, R.id.agenda_title_2, R.id.agenda_title_3)
    rows.forEachIndexed { index, row ->
        val event = events.getOrNull(index)
        views.setViewVisibility(row, if (event == null) View.GONE else View.VISIBLE)
        if (event != null) {
            val title = event.title.ifBlank { context.getString(R.string.agenda_untitled) }
            val time = agendaEventTime(context, event, now)
            views.setTextViewText(times[index], time)
            views.setTextViewText(titles[index], title)
            views.setContentDescription(row, "$time, $title")
            views.setOnClickPendingIntent(row, eventPendingIntent(context, id, event))
        }
    }
    return views
}

internal fun agendaEventTime(context: Context, event: EventOccurrence, now: ZonedDateTime): String {
    val date = event.startDate(now.zone).format(DateTimeFormatter.ofPattern("EEE d MMM"))
    if (event.allDay) {
        val last = event.endDateExclusive().minusDays(1)
        val dates = if (last > event.startDate(now.zone)) "$date – ${last.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))}" else date
        return "$dates · ${context.getString(R.string.agenda_all_day)}"
    }
    val start = Instant.ofEpochMilli(event.beginMillis).atZone(now.zone)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(now.zone)
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val time = start.format(DateTimeFormatter.ofPattern(pattern))
    val finish = end.format(DateTimeFormatter.ofPattern(if (start.toLocalDate() == end.toLocalDate()) pattern else "EEE d MMM $pattern"))
    return "$date · $time–$finish"
}
