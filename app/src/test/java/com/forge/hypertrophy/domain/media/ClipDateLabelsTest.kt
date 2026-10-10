package com.forge.hypertrophy.domain.media

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipDateLabelsTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun relativeAgeBuckets() {
        assertEquals(RelativeClipAge.Today, relativeClipAge(today, today))
        assertEquals(RelativeClipAge.Today, relativeClipAge(today.plusDays(2), today))
        assertEquals(RelativeClipAge.Yesterday, relativeClipAge(today.minusDays(1), today))
        assertEquals(RelativeClipAge.DaysAgo(6), relativeClipAge(today.minusDays(6), today))
        assertEquals(RelativeClipAge.WeeksAgo(1), relativeClipAge(today.minusDays(7), today))
        assertEquals(RelativeClipAge.WeeksAgo(2), relativeClipAge(today.minusDays(14), today))
        assertEquals(RelativeClipAge.WeeksAgo(3), relativeClipAge(today.minusDays(27), today))
        assertEquals(RelativeClipAge.MonthsAgo(1), relativeClipAge(today.minusDays(30), today))
        assertEquals(RelativeClipAge.MonthsAgo(2), relativeClipAge(today.minusDays(60), today))
    }

    @Test
    fun daysApartIsAbsolute() {
        assertEquals(0, daysApart(today, today))
        assertEquals(14, daysApart(today.minusDays(14), today))
        assertEquals(14, daysApart(today, today.minusDays(14)))
    }

    @Test
    fun relativeSpanBuckets() {
        assertEquals(RelativeClipSpan.SameDay, relativeClipSpan(0))
        assertEquals(RelativeClipSpan.Days(1), relativeClipSpan(1))
        assertEquals(RelativeClipSpan.Days(6), relativeClipSpan(6))
        assertEquals(RelativeClipSpan.Weeks(1), relativeClipSpan(7))
        assertEquals(RelativeClipSpan.Weeks(2), relativeClipSpan(14))
        assertEquals(RelativeClipSpan.Months(1), relativeClipSpan(30))
        assertEquals(RelativeClipSpan.Months(2), relativeClipSpan(60))
    }
}
