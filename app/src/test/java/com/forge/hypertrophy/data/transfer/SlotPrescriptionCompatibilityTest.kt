package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.data.db.TrainingConverters
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotPrescriptionCompatibilityTest {
    @Test
    fun snapshotWrittenBeforeTheNewFieldsDecodesWithTheirDefaults() {
        val writtenBeforeTheFields = """
            {
              "exerciseId": 7,
              "category": "COMPOUND",
              "sortOrder": 1,
              "metricType": "WEIGHT_REPS",
              "setsMin": 3,
              "setsMax": 5,
              "progressionRule": "LINEAR",
              "legacyCue": "brace"
            }
        """.trimIndent()

        val decoded = TrainingConverters().toPrescription(writtenBeforeTheFields)

        assertNull(decoded.holdTargetMaxSec)
        assertNull(decoded.notes)
        assertEquals(7L, decoded.exerciseId)
        assertEquals(SlotCategory.COMPOUND, decoded.category)
        assertEquals(1, decoded.sortOrder)
        assertEquals(MetricType.WEIGHT_REPS, decoded.metricType)
        assertEquals(3, decoded.setsMin)
        assertEquals(5, decoded.setsMax)
        assertEquals(ProgressionRule.LINEAR, decoded.progressionRule)
    }
}
