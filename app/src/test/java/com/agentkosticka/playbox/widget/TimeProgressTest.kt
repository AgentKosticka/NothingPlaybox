package com.agentkosticka.playbox.widget

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeProgressTest {
    @Test fun allFourBarsAdvanceWithinTheSameDay() {
        val start = ZonedDateTime.parse("2026-09-06T12:00:00+02:00[Europe/Prague]")
        val first = timeProgress(start)
        val next = timeProgress(start.plusMinutes(15))
        assertEquals(listOf("SUNDAY", "WEEK 36", "SEPTEMBER", "2026"), first.map { it.label })
        first.indices.forEach { assertTrue("Bar $it should advance", next[it].fraction > first[it].fraction) }
        assertEquals(12.25 / 24, next[0].fraction, .000001)
        assertEquals(.5, first[0].fraction, .000001)
    }

    @Test fun calendarPeriodsResetAtMidnightOnNewYear() {
        val bars = timeProgress(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
        assertEquals("WEEK 1", bars[1].label)
        bars.forEach { assertEquals(0.0, it.fraction, 0.0) }
    }

    @Test fun isoWeekCanBelongToPreviousYear() {
        assertEquals("WEEK 53", timeProgress(ZonedDateTime.parse("2021-01-01T12:00:00Z"))[1].label)
    }

    @Test fun leapDayUsesActualMonthAndYearLength() {
        val bars = timeProgress(ZonedDateTime.parse("2024-02-29T12:00:00Z"))
        assertEquals(28.5 / 29, bars[2].fraction, .000001)
        assertEquals(59.5 / 366, bars[3].fraction, .000001)
    }

    @Test fun daylightSavingUsesActualElapsedDuration() {
        val spring = timeProgress(ZonedDateTime.parse("2026-03-29T12:00:00+02:00[Europe/Prague]"))
        val autumn = timeProgress(ZonedDateTime.parse("2026-10-25T12:00:00+01:00[Europe/Prague]"))
        assertEquals(11.0 / 23, spring[0].fraction, .000001)
        assertEquals(13.0 / 25, autumn[0].fraction, .000001)
    }

    @Test fun weekStartChangesBothBoundaryAndWeekNumber() {
        val sunday = ZonedDateTime.parse("2026-09-06T12:00:00Z")
        val mondayWeek = timeProgress(sunday)
        val sundayWeek = timeProgress(sunday, java.time.DayOfWeek.SUNDAY)
        assertEquals(6.5 / 7, mondayWeek[1].fraction, .000001)
        assertEquals(.5 / 7, sundayWeek[1].fraction, .000001)
        assertEquals("WEEK 36", mondayWeek[1].label)
        assertEquals("WEEK 36", sundayWeek[1].label)
        java.time.DayOfWeek.entries.forEach { day ->
            val boundary = sunday.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(day)).atStartOfDay(sunday.zone)
            assertEquals(0.0, timeProgress(boundary, day)[1].fraction, 0.0)
        }
    }
}
