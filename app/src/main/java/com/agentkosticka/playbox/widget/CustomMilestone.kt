package com.agentkosticka.playbox.widget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Leap-day anniversaries fall on February 28 in non-leap years. Today remains zero. */
fun milestoneDate(date: LocalDate, repeatYearly: Boolean, today: LocalDate): LocalDate {
    if (!repeatYearly) return date
    val thisYear = date.withYear(today.year)
    return if (thisYear < today) date.withYear(today.year + 1) else thisYear
}

fun milestoneDays(date: LocalDate, repeatYearly: Boolean, today: LocalDate): Long =
    ChronoUnit.DAYS.between(today, milestoneDate(date, repeatYearly, today))
