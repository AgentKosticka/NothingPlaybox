package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.agentkosticka.playbox.calendar.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** One bounded query for all installed marker widgets in this refresh, never per cell. */
internal class CalendarWidgetDots(context: Context, private val now: ZonedDateTime, providers: List<Class<out UtilityDashboardWidget>>) {
    private val settings = buildMap {
        val manager = AppWidgetManager.getInstance(context)
        val store = WidgetInstanceSettings(context)
        providers.filter { it in calendarProviders }.forEach { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                store.load(id).agenda.takeIf { it.showMarkers }?.let { put(id, it) }
            }
        }
    }
    private val start = now.toLocalDate().withDayOfMonth(1).minusDays(7)
    private val end = now.toLocalDate().withDayOfMonth(1).plusMonths(1).plusDays(7)
    private val state = if (settings.isEmpty()) null else CalendarRepository(context).read(
        start.minusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli(),
        end.plusDays(2).atStartOfDay(now.zone).toInstant().toEpochMilli(),
        settings.values.map { it.calendarIds },
    )

    fun dates(id: Int, weekStart: DayOfWeek, provider: Class<*>): Set<LocalDate>? {
        val config = settings[id] ?: return null
        // An enabled feature with unavailable data shows no event dots, not stale ones.
        val data = state as? CalendarDataState.Available ?: return emptySet()
        val selected = selectedCalendarIds(config.calendarIds, data.calendars)
        val first = if (provider == MonthMatrixWidget::class.java) now.toLocalDate().withDayOfMonth(1)
            else now.toLocalDate().minusDays(((now.dayOfWeek.value - weekStart.value + 7) % 7).toLong())
        val last = if (provider == MonthMatrixWidget::class.java) first.plusMonths(1) else first.plusDays(7)
        return data.events.asSequence().filter { it.calendarId in selected && (config.showAllDay || !it.allDay) }
            .flatMap { occupiedDates(it, now.zone, first, last) }.toSet()
    }

    private companion object {
        val calendarProviders = setOf(MonthMatrixWidget::class.java, WeekStripWidget::class.java, WeekColumnWidget::class.java)
    }
}
