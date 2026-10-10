package com.forge.hypertrophy.ui.screens.media

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparison date copy (VM wiring uses [ClipDateLines.comparisonDates]). */
class VideoComparisonViewModelTest {
    private val today = LocalDate.of(2026, 10, 1)
    private val locale = Locale.UK

    @Test
    fun comparisonDatesIncludeRelativeAgeAndDaysApart() {
        val leftOn = today.minusDays(14)
        val rightOn = today
        val dates = ClipDateLines.comparisonDates(leftOn, rightOn, today, locale)
        assertTrue(dates.leftDateLine!!.contains("2 weeks ago"))
        assertTrue(dates.rightDateLine!!.contains("Today"))
        assertEquals("2 weeks apart", dates.apartLine)
    }

    @Test
    fun comparisonDatesOmitApartWhenEitherDateMissing() {
        val dates = ClipDateLines.comparisonDates(today, null, today, locale)
        assertTrue(dates.leftDateLine!!.contains("Today"))
        assertNull(dates.rightDateLine)
        assertNull(dates.apartLine)
    }
}
