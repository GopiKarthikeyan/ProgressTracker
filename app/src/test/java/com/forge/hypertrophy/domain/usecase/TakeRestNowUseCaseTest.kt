package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class TakeRestNowUseCaseTest {
    private val today = LocalDate.of(2026, 3, 16)
    private val streaks = StreakCalculator()
    private val useCase = TakeRestNowUseCase(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        streaks,
    )

    @Test
    fun rollingPullsRestForwardAndKeepsTheStreak() {
        val before = base()
        val streakBefore = streaks.streak(before, today)
        val applied = useCase.apply(before) as ScheduleEdit.Applied
        assertEquals(ProgramFixture.SUNDAY, applied.snapshot.rollingDayByDate.getValue(today))
        assertEquals(1, applied.snapshot.rollingIndex)
        assertTrue(today in applied.snapshot.autoCompletedRests)
        assertEquals(streakBefore + 1, applied.streak)
        assertEquals(applied.streak, streaks.streak(applied.snapshot, today))
    }

    @Test
    fun fixedModeIsRejected() {
        val before = base().copy(mode = ScheduleMode.FIXED)
        assertEquals(ScheduleEdit.Rejected, useCase.apply(before))
        assertEquals(streaks.streak(before, today), streaks.streak(before, today))
    }

    private fun base() = ProgramFixture.snapshot(
        mode = ScheduleMode.ROLLING,
        rollingIndex = 0,
        explicitCompletions = setOf(LocalDate.of(2026, 3, 14)),
        rollingDayByDate = mapOf(
            LocalDate.of(2026, 3, 14) to ProgramFixture.SATURDAY,
            LocalDate.of(2026, 3, 15) to ProgramFixture.SUNDAY,
        ),
    )
}
