package com.forge.hypertrophy.domain.dayplan

import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import org.junit.Assert.assertEquals
import org.junit.Test

class SchemeSummaryFormatterTest {
    @Test
    fun formatsStandardRepRange() {
        assertEquals(
            "3 x 6-8",
            SchemeSummaryFormatter.format(
                prescription(MetricType.WEIGHT_REPS, sets = 3, repsLow = 6, repsHigh = 8),
            ),
        )
    }

    @Test
    fun formatsRepLadder() {
        assertEquals(
            "12 Reps • 10 Reps • 8 Reps • 6 Reps",
            SchemeSummaryFormatter.format(
                prescription(
                    MetricType.WEIGHT_REPS,
                    sets = 4,
                    repsLow = 12,
                    repsHigh = 6,
                ),
            ),
        )
    }

    @Test
    fun formatsHold() {
        assertEquals(
            "4 x Hold",
            SchemeSummaryFormatter.format(
                prescription(MetricType.HOLD, sets = 4, repsLow = null, repsHigh = null),
            ),
        )
    }

    @Test
    fun formatsTimedBlock() {
        assertEquals(
            "10 Mins",
            SchemeSummaryFormatter.format(
                prescription(
                    MetricType.TIMED_BLOCK,
                    sets = 1,
                    repsLow = null,
                    repsHigh = null,
                    block = 600,
                ),
            ),
        )
    }

    @Test
    fun formatsChecklist() {
        assertEquals("15 Reps", SchemeSummaryFormatter.formatChecklist(15, null))
        assertEquals("10 Mins", SchemeSummaryFormatter.formatChecklist(null, 600))
    }

    private fun prescription(
        metric: MetricType,
        sets: Int,
        repsLow: Int?,
        repsHigh: Int?,
        block: Int? = null,
    ) = SlotPrescription(
        exerciseId = 1,
        category = SlotCategory.COMPOUND,
        sortOrder = 0,
        metricType = metric,
        setsMin = sets,
        setsMax = sets,
        repsLow = repsLow,
        repsHigh = repsHigh,
        blockDurationSec = block,
        progressionRule = ProgressionRule.DOUBLE,
    )
}
