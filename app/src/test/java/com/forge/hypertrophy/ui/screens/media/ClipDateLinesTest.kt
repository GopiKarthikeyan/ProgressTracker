package com.forge.hypertrophy.ui.screens.media

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipDateLinesTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val locale = Locale.UK

    @Test
    fun dateLineCombinesAbsoluteAndRelative() {
        assertEquals("10 Oct · Today", ClipDateLines.dateLine(today, today, locale))
        assertEquals("9 Oct · Yesterday", ClipDateLines.dateLine(today.minusDays(1), today, locale))
        assertEquals("4 Oct · 6 days ago", ClipDateLines.dateLine(today.minusDays(6), today, locale))
        assertEquals("3 Oct · 1 week ago", ClipDateLines.dateLine(today.minusDays(7), today, locale))
        assertEquals("26 Sept · 2 weeks ago", ClipDateLines.dateLine(today.minusDays(14), today, locale))
        assertEquals("10 Sept · 1 month ago", ClipDateLines.dateLine(today.minusDays(30), today, locale))
        assertEquals("11 Aug · 2 months ago", ClipDateLines.dateLine(today.minusDays(60), today, locale))
        assertEquals("10 Oct · 1 year ago", ClipDateLines.dateLine(today.minusDays(365), today, locale))
        assertEquals("10 Oct · 2 years ago", ClipDateLines.dateLine(today.minusDays(730), today, locale))
    }

    @Test
    fun apartLineCoversSameDayAndGaps() {
        assertEquals("Same day", ClipDateLines.apartLine(today, today))
        assertEquals("1 day apart", ClipDateLines.apartLine(today, today.minusDays(1)))
        assertEquals("6 days apart", ClipDateLines.apartLine(today, today.minusDays(6)))
        assertEquals("1 week apart", ClipDateLines.apartLine(today.minusDays(7), today))
        assertEquals("2 weeks apart", ClipDateLines.apartLine(today.minusDays(14), today))
        assertEquals("1 month apart", ClipDateLines.apartLine(today.minusDays(30), today))
        assertEquals("2 months apart", ClipDateLines.apartLine(today.minusDays(60), today))
        assertEquals("1 year apart", ClipDateLines.apartLine(today.minusDays(365), today))
        assertEquals("2 years apart", ClipDateLines.apartLine(today.minusDays(730), today))
    }
}
