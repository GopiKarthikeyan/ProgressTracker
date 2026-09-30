package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class SkipToNextUseCaseTest {
    private val today = LocalDate.of(2026, 3, 16)
    private val streaks = StreakCalculator()
    private val useCase = SkipToNextUseCase(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        streaks,
    )

    @Test
    fun rollingAdvancesWithoutAddingToday() {
        val before = ProgramFixture.snapshot(
            mode = ScheduleMode.ROLLING,
            rollingIndex = 0,
            explicitCompletions = setOf(LocalDate.of(2026, 3, 14)),
            rollingDayByDate = mapOf(
                LocalDate.of(2026, 3, 14) to ProgramFixture.SATURDAY,
                LocalDate.of(2026, 3, 15) to ProgramFixture.SUNDAY,
            ),
        )
        val streakBefore = streaks.streak(before, today)
        val applied = useCase.apply(before) as ScheduleEdit.Applied
        assertEquals(1, applied.snapshot.rollingIndex)
        assertEquals(ProgramFixture.TUESDAY, applied.snapshot.rollingDayByDate.getValue(today))
        assertFalse(today in applied.snapshot.explicitCompletions)
        assertFalse(today in applied.snapshot.autoCompletedRests)
        assertEquals(streakBefore, applied.streak)
    }

    @Test
    fun fixedModeIsRejected() {
        val before = ProgramFixture.snapshot(ScheduleMode.FIXED)
        assertEquals(ScheduleEdit.Rejected, useCase.apply(before))
    }
}
