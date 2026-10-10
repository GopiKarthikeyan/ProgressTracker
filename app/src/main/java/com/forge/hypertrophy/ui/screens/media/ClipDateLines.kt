package com.forge.hypertrophy.ui.screens.media

import com.forge.hypertrophy.domain.media.RelativeClipAge
import com.forge.hypertrophy.domain.media.RelativeClipSpan
import com.forge.hypertrophy.domain.media.daysApart
import com.forge.hypertrophy.domain.media.relativeClipAge
import com.forge.hypertrophy.domain.media.relativeClipSpan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds Gallery / Compare date copy. English matches [R.string] media_clip_* resources. */
object ClipDateLines {
    private fun absolute(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    private fun relative(age: RelativeClipAge): String = when (age) {
        RelativeClipAge.Today -> "Today"
        RelativeClipAge.Yesterday -> "Yesterday"
        is RelativeClipAge.DaysAgo -> if (age.days == 1) "1 day ago" else "${age.days} days ago"
        is RelativeClipAge.WeeksAgo -> if (age.weeks == 1) "1 week ago" else "${age.weeks} weeks ago"
        is RelativeClipAge.MonthsAgo -> if (age.months == 1) "1 month ago" else "${age.months} months ago"
    }

    private fun span(span: RelativeClipSpan): String = when (span) {
        RelativeClipSpan.SameDay -> "Same day"
        is RelativeClipSpan.Days -> if (span.days == 1) "1 day apart" else "${span.days} days apart"
        is RelativeClipSpan.Weeks -> if (span.weeks == 1) "1 week apart" else "${span.weeks} weeks apart"
        is RelativeClipSpan.Months -> if (span.months == 1) "1 month apart" else "${span.months} months apart"
    }

    fun dateLine(capturedOn: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val abs = absolute(capturedOn, locale)
        val rel = relative(relativeClipAge(capturedOn, today))
        return "$abs · $rel"
    }

    fun apartLine(a: LocalDate, b: LocalDate): String =
        span(relativeClipSpan(daysApart(a, b)))

    data class ComparisonDates(
        val leftDateLine: String?,
        val rightDateLine: String?,
        val apartLine: String?,
    )

    fun comparisonDates(
        leftOn: LocalDate?,
        rightOn: LocalDate?,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): ComparisonDates = ComparisonDates(
        leftDateLine = leftOn?.let { dateLine(it, today, locale) },
        rightDateLine = rightOn?.let { dateLine(it, today, locale) },
        apartLine = if (leftOn != null && rightOn != null) apartLine(leftOn, rightOn) else null,
    )
}
