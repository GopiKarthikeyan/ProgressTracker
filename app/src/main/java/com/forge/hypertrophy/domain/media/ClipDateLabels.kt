package com.forge.hypertrophy.domain.media

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

sealed interface RelativeClipAge {
    data object Today : RelativeClipAge
    data object Yesterday : RelativeClipAge
    data class DaysAgo(val days: Int) : RelativeClipAge
    data class WeeksAgo(val weeks: Int) : RelativeClipAge
    data class MonthsAgo(val months: Int) : RelativeClipAge
    data class YearsAgo(val years: Int) : RelativeClipAge
}

/** Span between two capture dates for Compare copy. */
sealed interface RelativeClipSpan {
    data object SameDay : RelativeClipSpan
    data class Days(val days: Int) : RelativeClipSpan
    data class Weeks(val weeks: Int) : RelativeClipSpan
    data class Months(val months: Int) : RelativeClipSpan
    data class Years(val years: Int) : RelativeClipSpan
}

/** Age of [capturedOn] relative to [today]. Future dates (clock skew) count as Today. */
fun relativeClipAge(capturedOn: LocalDate, today: LocalDate): RelativeClipAge {
    val days = ChronoUnit.DAYS.between(capturedOn, today)
    return when {
        days <= 0L -> RelativeClipAge.Today
        days == 1L -> RelativeClipAge.Yesterday
        days < 7L -> RelativeClipAge.DaysAgo(days.toInt())
        days < 28L -> RelativeClipAge.WeeksAgo((days / 7L).toInt())
        days < 365L -> RelativeClipAge.MonthsAgo((days / 30L).coerceAtLeast(1L).toInt())
        else -> RelativeClipAge.YearsAgo((days / 365L).coerceAtLeast(1L).toInt())
    }
}

/** Absolute calendar-day gap between two capture dates. */
fun daysApart(a: LocalDate, b: LocalDate): Int =
    ChronoUnit.DAYS.between(a, b).absoluteValue.toInt()

/** Maps an absolute day gap to week/month/year-aware Compare phrasing. */
fun relativeClipSpan(days: Int): RelativeClipSpan = when {
    days <= 0 -> RelativeClipSpan.SameDay
    days == 1 -> RelativeClipSpan.Days(1)
    days < 7 -> RelativeClipSpan.Days(days)
    days < 28 -> RelativeClipSpan.Weeks((days / 7).coerceAtLeast(1))
    days < 365 -> RelativeClipSpan.Months((days / 30).coerceAtLeast(1))
    else -> RelativeClipSpan.Years((days / 365).coerceAtLeast(1))
}
