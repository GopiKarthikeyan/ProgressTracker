package com.forge.hypertrophy.domain.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingAverageTest {
    @Test
    fun emptyInputProducesNoPoints() {
        assertTrue(MovingAverage.trailing(emptyList()).isEmpty())
    }

    @Test
    fun missingDaysAreLeftOutOfTheAverage() {
        val first = LocalDate.of(2026, 10, 1)
        val second = first.plusDays(1)
        val third = first.plusDays(2)
        val average = MovingAverage.trailing(
            listOf(
                DatedValue(first, 70.0),
                DatedValue(third, 90.0),
            ),
        )
        assertEquals(80.0, average.first { it.date == third }.value, 0.001)
        assertTrue(average.none { it.date == second && it.value == 0.0 })
        assertTrue(average.none { it.value == 0.0 })
    }

    @Test
    fun aSampleOlderThanTheWindowDoesNotPullTheAverage() {
        val early = LocalDate.of(2026, 10, 1)
        val later = early.plusDays(9)
        val average = MovingAverage.trailing(
            listOf(DatedValue(early, 70.0), DatedValue(later, 80.0)),
        )
        assertEquals(80.0, average.last().value, 0.001)
        assertEquals(later, average.last().date)
    }

    @Test
    fun aSingleSampleIsItsOwnAverage() {
        val day = LocalDate.of(2026, 10, 1)
        val average = MovingAverage.trailing(listOf(DatedValue(day, 82.5)))
        assertEquals(listOf(DatedValue(day, 82.5)), average)
    }
}
