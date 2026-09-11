package com.agentkosticka.playbox.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarLogicTest {
    private val utc = ZoneOffset.UTC
    private val now = ZonedDateTime.parse("2026-09-11T12:00:00Z")
    private fun event(id: Long, start: String, end: String, allDay: Boolean = false, calendar: Long = 1) =
        EventOccurrence(id, calendar, "Event $id", Instant.parse(start).toEpochMilli(), Instant.parse(end).toEpochMilli(), allDay, null)

    @Test fun unsetSelectionUsesVisibleButEmptySelectionStaysEmpty() {
        val calendars = listOf(DeviceCalendar(1, "Personal", null, true), DeviceCalendar(2, "Hidden", null, false))
        assertEquals(setOf(1L), selectedCalendarIds(null, calendars))
        assertEquals(emptySet<Long>(), selectedCalendarIds(emptySet(), calendars))
        assertEquals(setOf(2L), selectedCalendarIds(setOf(2, 99), calendars))
    }

    @Test fun ongoingThenAllDayThenUpcomingWithEndedAndDeselectedExcluded() {
        val ongoing = event(1, "2026-09-10T20:00:00Z", "2026-09-11T13:00:00Z")
        val day = event(2, "2026-09-11T00:00:00Z", "2026-09-12T00:00:00Z", true)
        val upcoming = event(3, "2026-09-11T13:00:00Z", "2026-09-11T14:00:00Z")
        val ended = event(4, "2026-09-11T10:00:00Z", "2026-09-11T12:00:00Z")
        val hidden = upcoming.copy(eventId = 5, calendarId = 2)
        val input = listOf(upcoming, hidden, day, ended, ongoing)
        assertEquals(listOf(ongoing, day, upcoming), agendaEvents(input, setOf(1), true, now, 3))
        assertEquals(listOf(ongoing, upcoming), agendaEvents(input, setOf(1), false, now, 3))
        assertEquals(listOf(ongoing), agendaEvents(input, setOf(1), true, now, 1))
    }

    @Test fun allDayRemainsOnItsDateAtBothTimezoneExtremes() {
        val allDay = event(1, "2026-09-11T00:00:00Z", "2026-09-12T00:00:00Z", true)
        val date = LocalDate.of(2026, 9, 11)
        listOf(ZoneOffset.ofHours(14), ZoneOffset.ofHours(-12)).forEach { zone ->
            assertEquals(setOf(date), occupiedDates(allDay, zone, date.minusDays(1), date.plusDays(2)))
            assertEquals(listOf(allDay), agendaEvents(listOf(allDay), setOf(1), true, date.atTime(23, 0).atZone(zone), 3))
            assertTrue(agendaEvents(listOf(allDay), setOf(1), true, date.plusDays(1).atStartOfDay(zone), 3).isEmpty())
        }
    }

    @Test fun midnightEndsAndMultiDayEventsUseHalfOpenRanges() {
        val start = LocalDate.of(2026, 9, 11)
        val midnight = event(1, "2026-09-11T22:00:00Z", "2026-09-13T00:00:00Z")
        assertEquals(setOf(start, start.plusDays(1)), occupiedDates(midnight, utc, start, start.plusDays(5)))
        val multi = midnight.copy(endMillis = Instant.parse("2026-09-13T08:00:00Z").toEpochMilli())
        assertEquals(setOf(start, start.plusDays(1), start.plusDays(2)), occupiedDates(multi, utc, start, start.plusDays(5)))
        assertEquals(setOf(start.plusDays(1)), occupiedDates(multi, utc, start.plusDays(1), start.plusDays(2)))
    }

    @Test fun dstAndMonthBoundaryClipping() {
        val event = event(1, "2026-03-28T23:00:00Z", "2026-03-30T22:00:00Z")
        val day = LocalDate.of(2026, 3, 29)
        assertEquals(setOf(day, day.plusDays(1)), occupiedDates(event, ZoneId.of("Europe/Prague"), day, day.plusDays(3)))
        val monthEvent = event(2, "2026-08-31T22:00:00Z", "2026-09-02T00:00:00Z")
        assertEquals(setOf(LocalDate.of(2026, 9, 1)), occupiedDates(monthEvent, utc, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
    }

    @Test fun simultaneousAndRecurringOccurrencesStayDistinctAndStable() {
        val first = event(7, "2026-09-11T13:00:00Z", "2026-09-11T14:00:00Z")
        val second = event(7, "2026-09-12T13:00:00Z", "2026-09-12T14:00:00Z")
        val simultaneous = first.copy(eventId = 8)
        assertEquals(listOf(first, simultaneous, second), agendaEvents(listOf(second, simultaneous, first), setOf(1), true, now, 3))
    }
}
