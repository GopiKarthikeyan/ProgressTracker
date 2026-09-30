package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class GetTodaysWorkoutUseCaseTest {
    @Test
    fun programFixtureCarriesTheRealWeek() {
        val monday = ProgramFixture.days.first { it.id == ProgramFixture.MONDAY }
        val friday = ProgramFixture.days.first { it.id == ProgramFixture.FRIDAY }
        val sunday = ProgramFixture.days.first { it.id == ProgramFixture.SUNDAY }
        assertTrue(sunday.isRest)
        assertEquals(7, ProgramFixture.days.size)
        val mondayRaise = monday.slots.first { it.prescription.exerciseId == ProgramFixture.LATERAL_RAISE }
        val fridayRaise = friday.slots.first { it.prescription.exerciseId == ProgramFixture.LATERAL_RAISE }
        assertEquals(12, mondayRaise.prescription.repsLow)
        assertEquals(12, mondayRaise.prescription.repsHigh)
        assertEquals(10, fridayRaise.prescription.repsLow)
        assertEquals(12, fridayRaise.prescription.repsHigh)
    }

    @Test
    fun table() {
        rows.forEach { row ->
            val plan = useCase(row.today).today(
                snapshot = ProgramFixture.snapshot(ScheduleMode.FIXED),
                check = com.forge.hypertrophy.domain.engine.ReadinessCheck(row.sleep, row.soreness, row.energy),
                transitionRestSeconds = 120,
                budgetSeconds = row.budget,
            )
            assertEquals(row.name, row.hold, plan!!.holdWeights)
            assertEquals(row.name, row.slotIds, plan.slots.map { it.id })
        }
    }

    private data class Row(
        val name: String,
        val today: LocalDate,
        val sleep: Int,
        val soreness: Int,
        val energy: Int,
        val budget: Int,
        val hold: Boolean,
        val slotIds: List<Long>,
    )

    private val rows = listOf(
        Row(
            name = "ready Monday keeps the full day",
            today = LocalDate.of(2026, 3, 16),
            sleep = 3,
            soreness = 3,
            energy = 3,
            budget = 0,
            hold = false,
            slotIds = listOf(11L, 12L, 13L, 14L, 15L),
        ),
        Row(
            name = "low readiness shortens Tuesday and holds weights",
            today = LocalDate.of(2026, 3, 17),
            sleep = 1,
            soreness = 1,
            energy = 1,
            budget = 930,
            hold = true,
            slotIds = listOf(21L, 23L),
        ),
    )

    private fun useCase(today: LocalDate) = GetTodaysWorkoutUseCase(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )
}
