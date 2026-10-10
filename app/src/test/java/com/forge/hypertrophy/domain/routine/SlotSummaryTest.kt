package com.forge.hypertrophy.domain.routine

import com.forge.hypertrophy.domain.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotSummaryTest {
    @Test
    fun weightRepsRange() {
        assertEquals(
            "3×8–12",
            slotPrescriptionSummary(3, 3, 8, 12, false, MetricType.WEIGHT_REPS, null),
        )
    }

    @Test
    fun amrapAndHold() {
        assertEquals(
            "3×AMRAP",
            slotPrescriptionSummary(3, 3, 8, 12, true, MetricType.WEIGHT_REPS, null),
        )
        assertEquals(
            "3×30s",
            slotPrescriptionSummary(3, 3, null, null, false, MetricType.HOLD, 30),
        )
    }

    @Test
    fun detailLineJoinsBaselineKg() {
        assertEquals("3×8–12", slotRowDetailLine("3×8–12", null))
        assertEquals("3×8–12 · 60 kg", slotRowDetailLine("3×8–12", 60.0))
        assertEquals("3×8–12 · 62.5 kg", slotRowDetailLine("3×8–12", 62.5))
    }
}
