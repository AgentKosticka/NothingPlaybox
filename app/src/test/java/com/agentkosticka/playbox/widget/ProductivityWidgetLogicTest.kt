package com.agentkosticka.playbox.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductivityWidgetLogicTest {
    @Test
    fun quickTasksNormalizeBoundsContentAndClearsBlankDoneState() {
        val state = QuickTasksState(
            title = "",
            tasks = listOf(
                QuickTask("A".repeat(80), true),
                QuickTask("", true),
                QuickTask("Third", false),
                QuickTask("Fourth", true),
                QuickTask("Fifth", true),
            ),
        ).normalized()

        assertEquals("", state.title)
        assertEquals("TODAY", state.displayTitle)
        assertEquals(4, state.tasks.size)
        assertEquals(60, state.tasks[0].text.length)
        assertFalse(state.tasks[1].done)
        assertEquals("Fourth", state.tasks[3].text)
    }

    @Test
    fun quickTasksOnlyToggleValidNonBlankItems() {
        val state = QuickTasksState(tasks = listOf(QuickTask("One"), QuickTask(), QuickTask(), QuickTask()))

        assertTrue(state.withDone(0, true).tasks[0].done)
        assertFalse(state.withDone(1, true).tasks[1].done)
        assertEquals(state.normalized(), state.withDone(9, true))
    }

    @Test
    fun dualClockRejectsInvalidZoneIds() {
        assertEquals("Europe/Prague", validatedZoneId("Europe/Prague"))
        assertEquals("UTC", validatedZoneId("not/a-real-zone"))
        assertEquals("UTC", validatedZoneId(null))
    }

    @Test
    fun habitTrackerBoundsHistoryAndComputesCurrentStreak() {
        val today = LocalDate.of(2026, 9, 11)
        val state = HabitTrackerState(
            name = "R".repeat(40),
            completedDays = setOf(
                today,
                today.minusDays(1),
                today.minusDays(3),
                today.minusDays(500),
                today.plusDays(1),
            ),
        ).normalized(today)

        assertEquals(24, state.name.length)
        assertEquals(setOf(today, today.minusDays(1), today.minusDays(3)), state.completedDays)
        assertEquals(2, state.currentStreak(today))
        assertTrue(state.isDone(today))
        assertFalse(state.toggle(today, today).isDone(today))
    }
}
