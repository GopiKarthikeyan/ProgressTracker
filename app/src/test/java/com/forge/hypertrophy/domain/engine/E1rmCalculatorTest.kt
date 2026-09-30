package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class E1rmCalculatorTest {
    @Test
    fun epleyUsesADoubleDivisorAndTwoDecimalPlaces() {
        assertEquals(116.67, E1rmCalculator.epley(100.0, 5)!!, 0.0)
        assertEquals(103.33, E1rmCalculator.epley(100.0, 1)!!, 0.0)
    }

    @Test
    fun fiveRepsIsNotCollapsedByIntegerDivision() {
        val estimate = E1rmCalculator.epley(100.0, 5)
        val integerDivision = 100.0 * (1 + 5 / 30)
        assertEquals(100.0, integerDivision, 0.0)
        assertEquals(116.67, estimate!!, 0.0)
    }

    @Test
    fun missingRepsOrWeightHasNoEstimate() {
        assertNull(E1rmCalculator.epley(100.0, 0))
        assertNull(E1rmCalculator.epley(0.0, 5))
    }
}
