package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetReadoutTest {
    @Test
    fun aHoldIsTimeOnly() {
        val readout = setReadout(MetricType.HOLD, Equipment.BODYWEIGHT)
        assertEquals(SetReadout.HOLD, readout)
        assertTrue(readout.showsHold())
        assertFalse(readout.showsWeight())
        assertFalse(readout.showsReps())
    }

    @Test
    fun bodyweightRepsStayReps() {
        val readout = setReadout(MetricType.REPS, Equipment.BODYWEIGHT)
        assertEquals(SetReadout.REPS, readout)
        assertTrue(readout.showsReps())
        assertFalse(readout.showsWeight())
    }

    @Test
    fun loadedSetsShowWeightAndReps() {
        assertEquals(SetReadout.WEIGHT_AND_REPS, setReadout(MetricType.WEIGHT_REPS, Equipment.BARBELL))
        assertTrue(SetReadout.WEIGHT_AND_REPS.showsWeight())
    }

    @Test
    fun weightedBodyweightHoldShowsAddedLoad() {
        assertEquals(
            SetReadout.WEIGHT_HOLD_AND_REPS,
            setReadout(MetricType.HOLD_OR_REPS, Equipment.WEIGHTED_BODYWEIGHT),
        )
    }

    @Test
    fun aBodyweightHoldOrRepsHasNoLoad() {
        val readout = setReadout(MetricType.HOLD_OR_REPS, Equipment.BODYWEIGHT)
        assertEquals(SetReadout.HOLD_AND_REPS, readout)
        assertFalse(readout.showsWeight())
        assertTrue(readout.showsHold())
        assertTrue(readout.showsReps())
    }
}
