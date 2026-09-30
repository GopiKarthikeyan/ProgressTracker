package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrDetectorTest {
    private val detector = PrDetector()

    @Test
    fun table() {
        assertNull(E1rmCalculator.epley(0.0, 10))
        cases.forEach { case ->
            assertEquals(case.name, case.expectedBest, detector.bestByExercise(case.history))
            if (case.candidate != null) {
                assertEquals(case.name, case.expectedIsPr, detector.isNewPr(case.history, case.candidate))
            }
        }
    }

    private data class Case(
        val name: String,
        val history: List<LiftSample>,
        val expectedBest: Map<Long, Double>,
        val candidate: LiftSample? = null,
        val expectedIsPr: Boolean = false,
    )

    private val cases = listOf(
        Case(
            name = "same exercise in two slots yields one best",
            history = listOf(
                LiftSample(1, 10, 100.0, 5),
                LiftSample(1, 11, 110.0, 3),
            ),
            expectedBest = mapOf(1L to 121.0),
        ),
        Case(
            name = "a tie is not a new PR",
            history = listOf(LiftSample(1, 10, 100.0, 5)),
            expectedBest = mapOf(1L to 116.67),
            candidate = LiftSample(1, 11, 100.0, 5),
            expectedIsPr = false,
        ),
        Case(
            name = "a heavier rounded e1RM is a new PR",
            history = listOf(LiftSample(1, 10, 100.0, 5)),
            expectedBest = mapOf(1L to 116.67),
            candidate = LiftSample(1, 11, 100.0, 6),
            expectedIsPr = true,
        ),
        Case(
            name = "bodyweight reps with no weight are excluded",
            history = listOf(LiftSample(2, 12, null, 10)),
            expectedBest = emptyMap(),
            candidate = LiftSample(2, 12, null, 10),
            expectedIsPr = false,
        ),
        Case(
            name = "empty history yields no PR",
            history = emptyList(),
            expectedBest = emptyMap(),
            candidate = LiftSample(1, 10, 100.0, 5),
            expectedIsPr = false,
        ),
    )
}
