package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.ProgramFixture
import com.forge.hypertrophy.domain.model.SlotCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortOnTimePlannerTest {
    private val planner = ShortOnTimePlanner()
    private val tuesday = ProgramFixture.days.first { it.id == ProgramFixture.TUESDAY }.slots

    @Test
    fun table() {
        cases.forEach { case ->
            val planned = planner.plan(case.slots, case.budget, case.transition)
            assertEquals(case.name, case.expectedSets, planned.map { it.id to it.prescription.setsMax })
        }
    }

    private data class Case(
        val name: String,
        val slots: List<com.forge.hypertrophy.domain.model.TrainingSlot>,
        val budget: Int,
        val transition: Int,
        val expectedSets: List<Pair<Long, Int>>,
    )

    private val cases = listOf(
        Case(
            name = "optional slot drops before isolation sets",
            slots = tuesday,
            budget = 930,
            transition = 120,
            expectedSets = listOf(
                21L to 3,
                23L to 3,
            ),
        ),
        Case(
            name = "isolation sets drop only after the optional slot is gone",
            slots = tuesday,
            budget = 840,
            transition = 120,
            expectedSets = listOf(
                21L to 3,
                23L to 2,
            ),
        ),
        Case(
            name = "a compound keeps its sets while an earlier isolation is cut",
            slots = listOf(tuesday[2], tuesday[0]),
            budget = 540 + 180 + 120,
            transition = 120,
            expectedSets = listOf(
                23L to 2,
                21L to 3,
            ),
        ),
        Case(
            name = "required compound sets stay when the budget cannot be met",
            slots = listOf(tuesday.first { it.prescription.category == SlotCategory.COMPOUND && !it.prescription.isOptional }),
            budget = 0,
            transition = 120,
            expectedSets = listOf(21L to 3),
        ),
    )
}
