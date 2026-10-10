package com.forge.hypertrophy.domain.cardio

import com.forge.hypertrophy.domain.model.CardioActivity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyCardioStatsTest {
    @Test
    fun sumsOnlyLogsInsideTheWeek() {
        val start = LocalDate.parse("2026-04-06")
        val end = LocalDate.parse("2026-04-12")
        val stats = weeklyCardioStats(
            listOf(
                CardioLogSample(LocalDate.parse("2026-04-05"), CardioActivity.RUNNING, 1000.0, 300),
                CardioLogSample(LocalDate.parse("2026-04-07"), CardioActivity.RUNNING, 2000.0, 600),
                CardioLogSample(LocalDate.parse("2026-04-08"), CardioActivity.CYCLING, 10000.0, 1800),
                CardioLogSample(LocalDate.parse("2026-04-13"), CardioActivity.SWIMMING, 500.0, 900),
            ),
            weekStart = start,
            weekEnd = end,
        )
        assertEquals(2, stats.sessions)
        assertEquals(2400, stats.durationSec)
        assertEquals(12000.0, stats.distanceM, 0.0)
        assertEquals(2, stats.byActivity.size)
        assertEquals(CardioActivity.CYCLING, stats.byActivity[0].activity)
        assertEquals(CardioActivity.RUNNING, stats.byActivity[1].activity)
    }
}
