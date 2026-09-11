package com.agentkosticka.playbox.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

data class DeviceCalendar(val id: Long, val name: String, val accountName: String?, val visible: Boolean)

data class EventOccurrence(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
) {
    fun startDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(beginMillis)
        .atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    fun endDateExclusive(): LocalDate = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
}

sealed interface CalendarDataState {
    data object PermissionMissing : CalendarDataState
    data object Error : CalendarDataState
    data class Available(val calendars: List<DeviceCalendar>, val events: List<EventOccurrence>) : CalendarDataState
}

fun selectedCalendarIds(selection: Set<Long>?, calendars: List<DeviceCalendar>): Set<Long> =
    selection?.intersect(calendars.map { it.id }.toSet()) ?: calendars.filter { it.visible }.map { it.id }.toSet()

/** Date-only events use calendar dates, never local conversions of UTC midnight. */
fun agendaEvents(events: List<EventOccurrence>, selected: Set<Long>, showAllDay: Boolean, now: ZonedDateTime, limit: Int): List<EventOccurrence> =
    events.filter { event ->
        event.calendarId in selected && if (event.allDay) {
            showAllDay && event.endDateExclusive() > now.toLocalDate()
        } else event.endMillis > now.toInstant().toEpochMilli()
    }.sortedWith(compareBy<EventOccurrence>(
        { maxOf(it.startDate(now.zone), now.toLocalDate()) },
        { if (it.allDay) 1 else if (it.beginMillis <= now.toInstant().toEpochMilli()) 0 else 2 },
        { it.beginMillis }, { it.eventId }, { it.calendarId },
    )).take(limit.coerceIn(1, 3))

/** Clip before expanding; an event ending at midnight never marks the following day. */
fun occupiedDates(event: EventOccurrence, zone: ZoneId, start: LocalDate, endExclusive: LocalDate): Set<LocalDate> {
    if (event.endMillis <= event.beginMillis || start >= endExclusive) return emptySet()
    val first = maxOf(start, event.startDate(zone))
    val last = minOf(endExclusive.minusDays(1), if (event.allDay) event.endDateExclusive().minusDays(1)
        else Instant.ofEpochMilli(event.endMillis - 1).atZone(zone).toLocalDate())
    if (first > last) return emptySet()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toSet()
}
