package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class SwapWithTodayUseCaseTest {
    private val today = LocalDate.of(2026, 3, 16)
    private val wednesday = today.plusDays(2)
    private val useCase = SwapWithTodayUseCase(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        StreakCalculator(),
    )

    @Test
    fun fixedSwapsSelectedDateWithToday() {
        val before = ProgramFixture.snapshot(ScheduleMode.FIXED)
        val applied = useCase.apply(before, wednesday) as ScheduleEdit.Applied
        assertEquals(ProgramFixture.WEDNESDAY, applied.snapshot.fixedSwaps.getValue(today))
        assertEquals(ProgramFixture.MONDAY, applied.snapshot.fixedSwaps.getValue(wednesday))
        assertEquals(ProgramFixture.WEDNESDAY, applied.snapshot.dayOn(today, today)!!.id)
        assertEquals(ProgramFixture.MONDAY, applied.snapshot.dayOn(wednesday, today)!!.id)
    }

    @Test
    fun rollingIsRejected() {
        val before = ProgramFixture.snapshot(ScheduleMode.ROLLING)
        assertEquals(ScheduleEdit.Rejected, useCase.apply(before, wednesday))
    }

    @Test
    fun sameDayIsRejected() {
        val before = ProgramFixture.snapshot(ScheduleMode.FIXED)
        assertEquals(ScheduleEdit.Rejected, useCase.apply(before, today))
    }
}
