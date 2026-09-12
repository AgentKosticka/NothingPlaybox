package com.agentkosticka.playbox.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchWidgetLogicTest {
    private val wall = 1_800_000_000_000L
    private val elapsed = 42_000_000L

    @Test
    fun focusStartPauseResumePreservesEndpointDerivedRemainingTime() {
        val started = FocusTimerEngine.startFocus(FocusTimerState(focusMinutes = 25), wall, elapsed)
        assertEquals(FocusPhase.FOCUS, started.phase)
        assertEquals(25 * 60_000L, started.remainingMillis(wall, elapsed))
        assertEquals(20 * 60_000L, started.remainingMillis(wall + 5 * 60_000L, elapsed + 5 * 60_000L))

        val paused = FocusTimerEngine.pause(started, wall + 5 * 60_000L, elapsed + 5 * 60_000L)
        assertEquals(FocusPhase.PAUSED_FOCUS, paused.phase)
        assertEquals(20 * 60_000L, paused.pausedRemainingMillis)
        assertEquals(20 * 60_000L, paused.remainingMillis(wall + 12 * 60_000L, elapsed + 12 * 60_000L))

        val resumed = FocusTimerEngine.resume(paused, wall + 12 * 60_000L, elapsed + 12 * 60_000L)
        assertEquals(FocusPhase.FOCUS, resumed.phase)
        assertEquals(20 * 60_000L, resumed.remainingMillis(wall + 12 * 60_000L, elapsed + 12 * 60_000L))
    }

    @Test
    fun focusCompletionAdvancesToBreakThenReturnsToIdle() {
        val focus = FocusTimerEngine.startFocus(
            FocusTimerState(focusMinutes = 25, breakMinutes = 5),
            wall,
            elapsed,
        )
        val breakState = FocusTimerEngine.reconcile(
            focus,
            wall + 25 * 60_000L,
            elapsed + 25 * 60_000L,
        )
        assertEquals(FocusPhase.BREAK, breakState.phase)
        assertEquals(1, breakState.completedSessions)
        assertEquals(5 * 60_000L, breakState.remainingMillis(wall + 25 * 60_000L, elapsed + 25 * 60_000L))

        val idle = FocusTimerEngine.reconcile(
            breakState,
            wall + 30 * 60_000L,
            elapsed + 30 * 60_000L,
        )
        assertEquals(FocusPhase.IDLE, idle.phase)
        assertEquals(1, idle.completedSessions)
        assertEquals(25 * 60_000L, idle.remainingMillis(wall + 30 * 60_000L, elapsed + 30 * 60_000L))
    }

    @Test
    fun focusDelayedCompletionCarriesOverdueTimeThroughBreak() {
        val focus = FocusTimerEngine.startFocus(
            FocusTimerState(focusMinutes = 25, breakMinutes = 5),
            wall,
            elapsed,
        )

        val lateBreak = FocusTimerEngine.reconcile(
            focus,
            wall + 28 * 60_000L,
            elapsed + 28 * 60_000L,
        )
        assertEquals(FocusPhase.BREAK, lateBreak.phase)
        assertEquals(1, lateBreak.completedSessions)
        assertEquals(2 * 60_000L, lateBreak.remainingMillis(wall + 28 * 60_000L, elapsed + 28 * 60_000L))

        val fullyMissedBreak = FocusTimerEngine.reconcile(
            focus,
            wall + 31 * 60_000L,
            elapsed + 31 * 60_000L,
        )
        assertEquals(FocusPhase.IDLE, fullyMissedBreak.phase)
        assertEquals(1, fullyMissedBreak.completedSessions)
        assertEquals(25 * 60_000L, fullyMissedBreak.remainingMillis(wall + 31 * 60_000L, elapsed + 31 * 60_000L))
    }

    @Test
    fun focusResetKeepsConfiguredDurationsAndClearsRunningSession() {
        val running = FocusTimerEngine.startFocus(
            FocusTimerState(focusMinutes = 45, breakMinutes = 10, completedSessions = 3),
            wall,
            elapsed,
        )
        val reset = FocusTimerEngine.reset(running)
        assertEquals(FocusPhase.IDLE, reset.phase)
        assertEquals(45, reset.focusMinutes)
        assertEquals(10, reset.breakMinutes)
        assertEquals(3, reset.completedSessions)
        assertEquals(45 * 60_000L, reset.pausedRemainingMillis)
        assertEquals(0L, reset.endWallMillis)
    }

    @Test
    fun focusStyleCyclesThroughAllPublicAppearanceModes() {
        assertEquals(WidgetVisualStyle.DYNAMIC, WidgetVisualStyle.CLASSIC.next())
        assertEquals(WidgetVisualStyle.GLASS, WidgetVisualStyle.DYNAMIC.next())
        assertEquals(WidgetVisualStyle.HIGH_CONTRAST, WidgetVisualStyle.GLASS.next())
        assertEquals(WidgetVisualStyle.CLASSIC, WidgetVisualStyle.HIGH_CONTRAST.next())
    }

    @Test
    fun goalRollsPreviousDayIntoHistoryAndStartsFreshToday() {
        val yesterday = LocalDate.of(2026, 9, 11).toEpochDay()
        val today = yesterday + 1
        val rolled = GoalTrackerState(
            preset = GoalPreset.WATER,
            value = 8,
            dayEpoch = yesterday,
        ).rolledTo(today)

        assertEquals(0, rolled.value)
        assertEquals(today, rolled.dayEpoch)
        assertEquals(8, rolled.history[yesterday])
    }

    @Test
    fun goalStreakRemainsActiveUntilTodayEnds() {
        val today = LocalDate.of(2026, 9, 12).toEpochDay()
        val state = GoalTrackerState(
            preset = GoalPreset.WATER,
            value = 3,
            dayEpoch = today,
            history = mapOf(today - 1 to 8, today - 2 to 9, today - 3 to 2),
        )
        assertEquals(2, state.streak(today))

        val completedToday = state.copy(value = 8)
        assertEquals(3, completedToday.streak(today))
    }

    @Test
    fun goalNormalizationBoundsAndPrunesHistory() {
        val today = LocalDate.of(2026, 9, 12).toEpochDay()
        val state = GoalTrackerState(
            preset = GoalPreset.CUSTOM,
            value = 200_000,
            dayEpoch = today + 10,
            history = mapOf(
                today to 4,
                today - 90 to 5,
                today - 91 to 6,
                today + 1 to 7,
            ),
        ).normalized(today)

        assertEquals(100_000, state.value)
        assertEquals(today, state.dayEpoch)
        assertEquals(mapOf(today to 4, today - 90 to 5), state.history)
    }

    @Test
    fun goalPresetsExposeSafeTargetsAndSteps() {
        assertEquals(8, GoalPreset.WATER.target)
        assertEquals(1, GoalPreset.WATER.step)
        assertEquals(30, GoalPreset.READ.target)
        assertEquals(5, GoalPreset.MOVE.step)
        assertTrue(GoalPreset.entries.all { it.target > 0 && it.step > 0 })
        assertFalse(GoalPreset.entries.isEmpty())
    }
}
