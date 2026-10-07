package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoRegulationAdvisorTest {
    private val advisor = AutoRegulationAdvisor()

    @Test
    fun rpeOfNineDoesNotSuggestAndTenDoes() {
        assertNull(advisor.advise(lastRpe = 9.0, currentWeightKg = 35.0, incrementKg = 2.5))
        assertNotNull(advisor.advise(lastRpe = 10.0, currentWeightKg = 35.0, incrementKg = 2.5))
    }

    @Test
    fun suggestionDropsOneIncrement() {
        val suggestion = advisor.advise(lastRpe = 10.0, currentWeightKg = 35.0, incrementKg = 2.5)
        assertEquals(10.0, suggestion!!.lastRpe, 0.0)
        assertEquals(32.5, suggestion.suggestedWeightKg, 0.0)
    }

    @Test
    fun decliningLeavesTheOriginalTargetIntact() {
        val suggestion = advisor.advise(lastRpe = 10.0, currentWeightKg = 35.0, incrementKg = 2.5)
        assertEquals(35.0, advisor.weightAfterDecision(35.0, suggestion, accepted = false), 0.0)
        assertEquals(32.5, advisor.weightAfterDecision(35.0, suggestion, accepted = true), 0.0)
    }
}
