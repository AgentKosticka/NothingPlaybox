package com.agentkosticka.playbox.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CustomMilestoneTest {
    @Test fun datesAreCalendarDaysIncludingTodayAndPastDates() {
        val today = LocalDate.of(2026, 9, 11)
        assertEquals(0L, milestoneDays(today, false, today))
        assertEquals(23L, milestoneDays(today.plusDays(23), false, today))
        assertEquals(-1L, milestoneDays(today.minusDays(1), false, today))
    }
    @Test fun annualDatesRollAfterTodayAndLeapDaysClamp() {
        val birthday = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2026, 2, 28), milestoneDate(birthday, true, LocalDate.of(2026, 2, 28)))
        assertEquals(LocalDate.of(2027, 2, 28), milestoneDate(birthday, true, LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), milestoneDate(birthday, true, LocalDate.of(2027, 3, 1)))
    }
}
