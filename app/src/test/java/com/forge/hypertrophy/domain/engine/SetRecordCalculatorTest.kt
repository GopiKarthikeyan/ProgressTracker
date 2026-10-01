package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SessionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetRecordCalculatorTest {
    private val calculator = SetRecordCalculator()

    @Test
    fun bestWeightTimesRepsPrefersTheHeavierTie() {
        val best = calculator.bestWeightTimesReps(
            listOf(
                LiftSample(1, 1, 10.0, 3),
                LiftSample(1, 1, 15.0, 2),
                LiftSample(1, 1, null, 8),
                LiftSample(2, 2, 0.0, 5),
            ),
        )
        assertEquals(WeightRepsBest(15.0, 2), best[1])
        assertNull(best[2])
    }

    @Test
    fun maxUnbrokenHoldIgnoresZero() {
        val best = calculator.maxUnbrokenHold(
            listOf(
                HoldSample(4, 0),
                HoldSample(4, 8),
                HoldSample(4, 12),
                HoldSample(5, 3),
            ),
        )
        assertEquals(12, best[4])
        assertEquals(3, best[5])
    }
}

class HeatmapTest {
    @Test
    fun gymWinsOverCardioOnTheSameDay() {
        assertEquals(
            SessionKind.GYM,
            heatmapKind(setOf(SessionKind.CARDIO, SessionKind.GYM, SessionKind.REST)),
        )
    }

    @Test
    fun restIsShownWhenItIsTheOnlyKind() {
        assertEquals(SessionKind.REST, heatmapKind(setOf(SessionKind.REST)))
    }

    @Test
    fun anEmptyDayHasNoKind() {
        assertNull(heatmapKind(emptySet()))
    }

    @Test
    fun activeRecoveryIsDistinctFromRest() {
        assertEquals(SessionKind.ACTIVE_RECOVERY, heatmapKind(setOf(SessionKind.ACTIVE_RECOVERY)))
    }
}
