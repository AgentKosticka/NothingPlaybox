package com.agentkosticka.playbox.widget

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class TimeBar(val label: String, val fraction: Double)

/** Calendar boundaries in the device zone, including short/long DST days and ISO weeks. */
fun timeProgress(now: ZonedDateTime, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<TimeBar> {
    val date = now.toLocalDate()
    val zone = now.zone
    val day = date.atStartOfDay(zone)
    val week = date.with(TemporalAdjusters.previousOrSame(weekStart))
    // Keep the ISO four-day rule for week 1, with the user's chosen first weekday.
    val weekFields = WeekFields.of(weekStart, 4)
    val month = date.withDayOfMonth(1)
    val year = date.withDayOfYear(1)
    fun bar(label: String, start: ZonedDateTime, end: ZonedDateTime) = TimeBar(
        label,
        (Duration.between(start, now).toMillis().toDouble() / Duration.between(start, end).toMillis()).coerceIn(0.0, 1.0),
    )
    return listOf(
        bar(now.dayOfWeek.name, day, date.plusDays(1).atStartOfDay(zone)),
        bar("WEEK ${now.get(weekFields.weekOfWeekBasedYear())}", week.atStartOfDay(zone), week.plusWeeks(1).atStartOfDay(zone)),
        bar(now.month.name.uppercase(Locale.ROOT), month.atStartOfDay(zone), month.plusMonths(1).atStartOfDay(zone)),
        bar(now.year.toString(), year.atStartOfDay(zone), year.plusYears(1).atStartOfDay(zone)),
    )
}
