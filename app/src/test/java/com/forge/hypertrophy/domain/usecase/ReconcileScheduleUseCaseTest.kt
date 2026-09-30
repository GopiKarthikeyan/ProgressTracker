package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class ReconcileScheduleUseCaseTest {
    private val today = LocalDate.of(2026, 3, 16)
    private val useCase = ReconcileScheduleUseCase(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )

    @Test
    fun table() {
        cases.forEach { case ->
            val once = useCase.reconcile(case.start)
            assertEquals(case.name, case.expectedIndex, once.rollingIndex)
            assertEquals(case.name, case.expectedRests, once.autoCompletedRests)
            assertEquals(case.name, today.minusDays(1), once.lastReconciled)
            val twice = useCase.reconcile(once)
            assertEquals(case.name, once, twice)
        }
    }

    private data class Case(
        val name: String,
        val start: ScheduleSnapshot,
        val expectedIndex: Int,
        val expectedRests: Set<LocalDate>,
    )

    private val cases = listOf(
        Case(
            name = "FIXED 1 day missed",
            start = start(ScheduleMode.FIXED, missedDays = 1),
            expectedIndex = 0,
            expectedRests = setOf(LocalDate.of(2026, 3, 15)),
        ),
        Case(
            name = "FIXED 5 days missed",
            start = start(ScheduleMode.FIXED, missedDays = 5),
            expectedIndex = 0,
            expectedRests = setOf(LocalDate.of(2026, 3, 15)),
        ),
        Case(
            name = "FIXED 40 days missed crosses a month",
            start = start(ScheduleMode.FIXED, missedDays = 40),
            expectedIndex = 0,
            expectedRests = setOf(
                LocalDate.of(2026, 2, 8),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 2, 22),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 15),
            ),
        ),
        Case(
            name = "FIXED zero days missed is idempotent",
            start = start(ScheduleMode.FIXED, missedDays = 0),
            expectedIndex = 0,
            expectedRests = emptySet(),
        ),
        Case(
            name = "ROLLING 1 day missed",
            start = start(ScheduleMode.ROLLING, missedDays = 1),
            expectedIndex = 1,
            expectedRests = emptySet(),
        ),
        Case(
            name = "ROLLING 5 days missed",
            start = start(ScheduleMode.ROLLING, missedDays = 5),
            expectedIndex = 5,
            expectedRests = emptySet(),
        ),
        Case(
            name = "ROLLING 40 days missed crosses a month",
            start = start(ScheduleMode.ROLLING, missedDays = 40),
            expectedIndex = 5,
            expectedRests = setOf(
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 2, 17),
                LocalDate.of(2026, 2, 24),
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 10),
            ),
        ),
        Case(
            name = "ROLLING zero days missed is idempotent",
            start = start(ScheduleMode.ROLLING, missedDays = 0),
            expectedIndex = 0,
            expectedRests = emptySet(),
        ),
    )

    private fun start(mode: ScheduleMode, missedDays: Int): ScheduleSnapshot {
        val last = if (missedDays == 0) today.minusDays(1) else today.minusDays(missedDays.toLong() + 1)
        return ProgramFixture.snapshot(mode = mode, rollingIndex = 0, lastReconciled = last)
    }
}
