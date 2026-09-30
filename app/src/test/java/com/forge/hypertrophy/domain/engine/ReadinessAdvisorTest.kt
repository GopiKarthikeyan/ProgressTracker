package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessAdvisorTest {
    private val advisor = ReadinessAdvisor()

    @Test
    fun table() {
        cases.forEach { case ->
            assertEquals(
                case.name,
                case.expected,
                advisor.advise(ReadinessCheck(case.sleep, case.soreness, case.energy)),
            )
        }
    }

    private data class Case(
        val name: String,
        val sleep: Int?,
        val soreness: Int?,
        val energy: Int?,
        val expected: ReadinessAdvice,
    )

    private val cases = listOf(
        Case("any component null", null, 1, 1, ReadinessAdvice.NONE),
        Case("all 1s sum 3", 1, 1, 1, ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS),
        Case("sum 4", 1, 1, 2, ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS),
        Case("sum 5", 1, 2, 2, ReadinessAdvice.NONE),
        Case("all 3s sum 9", 3, 3, 3, ReadinessAdvice.NONE),
        Case("out-of-range 7 is summed as 7", 7, 0, 0, ReadinessAdvice.NONE),
    )
}
