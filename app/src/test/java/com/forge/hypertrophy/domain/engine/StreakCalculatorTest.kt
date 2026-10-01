package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val calculator = StreakCalculator()

    @Test
    fun table() {
        cases.forEach { case ->
            val snapshot = ProgramFixture.snapshot(
                mode = case.mode,
                explicitCompletions = case.explicit,
                rollingDayByDate = case.rolling,
            )
            assertEquals(case.name, case.expected, calculator.streak(snapshot, case.today))
        }
    }

    private data class Case(
        val name: String,
        val mode: ScheduleMode,
        val today: LocalDate,
        val explicit: Set<LocalDate>,
        val rolling: Map<LocalDate, Long>,
        val expected: Int,
    )

    private val cases = listOf(
        Case(
            name = "FIXED month boundary",
            mode = ScheduleMode.FIXED,
            today = LocalDate.of(2025, 2, 3),
            explicit = setOf(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 1)),
            rolling = emptyMap(),
            expected = 3,
        ),
        Case(
            name = "ROLLING month boundary",
            mode = ScheduleMode.ROLLING,
            today = LocalDate.of(2025, 2, 3),
            explicit = setOf(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 1)),
            rolling = mapOf(
                LocalDate.of(2025, 1, 31) to ProgramFixture.FRIDAY,
                LocalDate.of(2025, 2, 1) to ProgramFixture.SATURDAY,
                LocalDate.of(2025, 2, 2) to ProgramFixture.SUNDAY,
                LocalDate.of(2025, 2, 3) to ProgramFixture.MONDAY,
            ),
            expected = 3,
        ),
        Case(
            name = "FIXED year boundary",
            mode = ScheduleMode.FIXED,
            today = LocalDate.of(2026, 1, 2),
            explicit = setOf(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)),
            rolling = emptyMap(),
            expected = 2,
        ),
        Case(
            name = "ROLLING year boundary",
            mode = ScheduleMode.ROLLING,
            today = LocalDate.of(2026, 1, 2),
            explicit = setOf(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)),
            rolling = mapOf(
                LocalDate.of(2025, 12, 31) to ProgramFixture.WEDNESDAY,
                LocalDate.of(2026, 1, 1) to ProgramFixture.THURSDAY,
                LocalDate.of(2026, 1, 2) to ProgramFixture.FRIDAY,
            ),
            expected = 2,
        ),
        Case(
            name = "FIXED missed training day breaks the streak",
            mode = ScheduleMode.FIXED,
            today = LocalDate.of(2025, 2, 1),
            explicit = setOf(LocalDate.of(2025, 1, 30)),
            rolling = emptyMap(),
            expected = 0,
        ),
        Case(
            name = "ROLLING missed training day breaks the streak",
            mode = ScheduleMode.ROLLING,
            today = LocalDate.of(2025, 2, 1),
            explicit = setOf(LocalDate.of(2025, 1, 30)),
            rolling = mapOf(
                LocalDate.of(2025, 1, 30) to ProgramFixture.THURSDAY,
                LocalDate.of(2025, 1, 31) to ProgramFixture.FRIDAY,
                LocalDate.of(2025, 2, 1) to ProgramFixture.SATURDAY,
            ),
            expected = 0,
        ),
        Case(
            name = "FIXED missed rest day does not break the streak",
            mode = ScheduleMode.FIXED,
            today = LocalDate.of(2025, 2, 3),
            explicit = setOf(LocalDate.of(2025, 2, 1)),
            rolling = emptyMap(),
            expected = 2,
        ),
        Case(
            name = "ROLLING missed rest day does not break the streak",
            mode = ScheduleMode.ROLLING,
            today = LocalDate.of(2025, 2, 2),
            explicit = emptySet(),
            rolling = mapOf(
                LocalDate.of(2025, 2, 1) to ProgramFixture.SUNDAY,
                LocalDate.of(2025, 2, 2) to ProgramFixture.MONDAY,
            ),
            expected = 1,
        ),
        Case(
            name = "FIXED rest day auto-completes when the day has ended",
            mode = ScheduleMode.FIXED,
            today = LocalDate.of(2025, 2, 3),
            explicit = emptySet(),
            rolling = emptyMap(),
            expected = 1,
        ),
    )

    @Test
    fun bestStreakKeepsTheLongerEarlierRun() {
        val today = LocalDate.of(2026, 10, 5)
        val snapshot = ProgramFixture.snapshot(
            mode = ScheduleMode.ROLLING,
            explicitCompletions = setOf(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 4),
            ),
            rollingDayByDate = (1..5).associate { LocalDate.of(2026, 10, it) to ProgramFixture.MONDAY },
        )
        assertEquals(1, calculator.streak(snapshot, today))
        assertEquals(2, calculator.bestStreak(snapshot, today))
    }
}
