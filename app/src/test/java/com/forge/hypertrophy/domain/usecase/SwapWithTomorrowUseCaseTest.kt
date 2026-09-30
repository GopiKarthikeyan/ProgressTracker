package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class SwapWithTomorrowUseCaseTest {
    private val today = LocalDate.of(2026, 3, 16)
    private val tomorrow = today.plusDays(1)
    private val streaks = StreakCalculator()
    private val useCase = SwapWithTomorrowUseCase(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        streaks,
    )

    @Test
    fun fixedSwapsTheTwoDaysAndKeepsTheStreak() {
        val before = ProgramFixture.snapshot(
            mode = ScheduleMode.FIXED,
            explicitCompletions = setOf(LocalDate.of(2026, 3, 14)),
        )
        val streakBefore = streaks.streak(before, today)
        val applied = useCase.apply(before) as ScheduleEdit.Applied
        assertEquals(ProgramFixture.TUESDAY, applied.snapshot.fixedSwaps.getValue(today))
        assertEquals(ProgramFixture.MONDAY, applied.snapshot.fixedSwaps.getValue(tomorrow))
        assertEquals(streakBefore, applied.streak)
        assertEquals(
            ProgramFixture.TUESDAY,
            applied.snapshot.dayOn(today, today)!!.id,
        )
    }

    @Test
    fun rollingModeIsRejected() {
        val before = ProgramFixture.snapshot(ScheduleMode.ROLLING)
        assertEquals(ScheduleEdit.Rejected, useCase.apply(before))
    }
}
