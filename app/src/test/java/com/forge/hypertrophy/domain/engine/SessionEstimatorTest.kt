package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.TrainingSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEstimatorTest {
    private val estimator = SessionEstimator()

    @Test
    fun table() {
        cases.forEach { case ->
            assertEquals(case.name, case.expected, estimator.estimate(case.slots, case.transition))
        }
    }

    private data class Case(
        val name: String,
        val slots: List<TrainingSlot>,
        val transition: Int,
        val expected: Int,
    )

    private val cases = listOf(
        Case(
            name = "superset counts its transition once",
            slots = listOf(
                work(1, sets = 1, rest = 100, superset = 1),
                work(2, sets = 1, rest = 100, superset = 1),
                work(3, sets = 1, rest = 100, superset = null),
            ),
            transition = 50,
            expected = 350,
        ),
        Case(
            name = "separate slots each take a transition",
            slots = listOf(
                work(1, sets = 1, rest = 100, superset = null),
                work(2, sets = 1, rest = 100, superset = null),
                work(3, sets = 1, rest = 100, superset = null),
            ),
            transition = 50,
            expected = 400,
        ),
        Case(
            name = "as needed rest contributes no planned seconds",
            slots = listOf(work(1, sets = 2, rest = 90, asNeeded = true, block = 600)),
            transition = 120,
            expected = 600,
        ),
        Case(
            name = "zero-slot day",
            slots = emptyList(),
            transition = 120,
            expected = 0,
        ),
    )

    private fun work(
        id: Long,
        sets: Int,
        rest: Int,
        superset: Int? = null,
        asNeeded: Boolean = false,
        block: Int? = null,
    ) = TrainingSlot(
        id = id,
        prescription = SlotPrescription(
            exerciseId = id,
            category = SlotCategory.COMPOUND,
            sortOrder = id.toInt(),
            supersetGroup = superset,
            metricType = MetricType.WEIGHT_REPS,
            setsMin = sets,
            setsMax = sets,
            restMinSec = rest,
            restMaxSec = rest,
            restAsNeeded = asNeeded,
            blockDurationSec = block,
            progressionRule = ProgressionRule.NONE,
        ),
    )
}
