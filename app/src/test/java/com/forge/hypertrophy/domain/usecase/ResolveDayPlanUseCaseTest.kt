package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class ResolveDayPlanUseCaseTest {
    private val today = LocalDate.of(2026, 3, 16) // Monday
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase = ResolveDayPlanUseCase(clock)

    @Test
    fun fixedMapsAnyWeekdayAsCommitted() {
        val snapshot = ProgramFixture.snapshot(ScheduleMode.FIXED)
        val wednesday = today.plusDays(2)
        val resolved = useCase.resolve(snapshot, wednesday, transitionRestSeconds = 60)
        assertEquals(DayPlanCommitment.COMMITTED, resolved.commitment)
        assertEquals(ProgramFixture.WEDNESDAY, resolved.day!!.id)
    }

    @Test
    fun rollingTodayIsCommittedFromPointer() {
        val snapshot = ProgramFixture.snapshot(ScheduleMode.ROLLING, rollingIndex = 1)
        val resolved = useCase.resolve(snapshot, today, transitionRestSeconds = 0)
        assertEquals(DayPlanCommitment.COMMITTED, resolved.commitment)
        assertEquals(ProgramFixture.TUESDAY, resolved.day!!.id)
    }

    @Test
    fun rollingFutureIsProjectedFromPointer() {
        val snapshot = ProgramFixture.snapshot(ScheduleMode.ROLLING, rollingIndex = 0)
        val tomorrow = today.plusDays(1)
        val resolved = useCase.resolve(snapshot, tomorrow, transitionRestSeconds = 0)
        assertEquals(DayPlanCommitment.PROJECTED, resolved.commitment)
        assertEquals(ProgramFixture.TUESDAY, resolved.day!!.id)
    }

    @Test
    fun rollingPastWithoutMappingIsUnresolved() {
        val snapshot = ProgramFixture.snapshot(ScheduleMode.ROLLING, rollingIndex = 0)
        val resolved = useCase.resolve(snapshot, today.minusDays(3), transitionRestSeconds = 0)
        assertEquals(DayPlanCommitment.UNRESOLVED, resolved.commitment)
        assertNull(resolved.day)
    }

    @Test
    fun rollingMappedPastIsCommitted() {
        val past = today.minusDays(1)
        val snapshot = ProgramFixture.snapshot(
            mode = ScheduleMode.ROLLING,
            rollingIndex = 1,
            rollingDayByDate = mapOf(past to ProgramFixture.MONDAY),
        )
        val resolved = useCase.resolve(snapshot, past, transitionRestSeconds = 0)
        assertEquals(DayPlanCommitment.COMMITTED, resolved.commitment)
        assertEquals(ProgramFixture.MONDAY, resolved.day!!.id)
        assertTrue(resolved.estimatedSeconds >= 0)
    }
}
