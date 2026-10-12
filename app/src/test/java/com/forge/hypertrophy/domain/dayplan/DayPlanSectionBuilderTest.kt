package com.forge.hypertrophy.domain.dayplan

import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.TrainingSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanSectionBuilderTest {
    @Test
    fun omitsEmptyCategories() {
        val sections = DayPlanSectionBuilder.build(
            slots = listOf(slot(1, SlotCategory.COMPOUND)),
            checklist = emptyList(),
            exerciseNames = mapOf(1L to "Squat"),
        )
        assertEquals(listOf(DayPlanSectionKind.COMPOUND), sections.map { it.kind })
    }

    @Test
    fun groupsGiantSetOfThree() {
        val slots = listOf(
            slot(1, SlotCategory.MOBILITY, group = 1),
            slot(2, SlotCategory.MOBILITY, group = 1),
            slot(3, SlotCategory.MOBILITY, group = 1),
        )
        val sections = DayPlanSectionBuilder.build(
            slots = slots,
            checklist = emptyList(),
            exerciseNames = mapOf(1L to "A", 2L to "B", 3L to "C"),
        )
        val rows = sections.single().rows.filterIsInstance<DayPlanRow.Slot>()
        assertEquals(3, rows.size)
        assertEquals("GIANT SET: Follow sequence", rows.first().groupLabel)
        assertTrue(rows.all { it.inSupersetGroup })
        assertTrue(rows.last().isGroupEnd)
    }

    @Test
    fun groupsSupersetOfTwo() {
        val slots = listOf(
            slot(1, SlotCategory.CORE, group = 2),
            slot(2, SlotCategory.CORE, group = 2),
        )
        val rows = DayPlanSectionBuilder.build(
            slots = slots,
            checklist = emptyList(),
            exerciseNames = mapOf(1L to "A", 2L to "B"),
        ).single().rows.filterIsInstance<DayPlanRow.Slot>()
        assertEquals("SUPERSET", rows.first().groupLabel)
    }

    private fun slot(exerciseId: Long, category: SlotCategory, group: Int? = null) = TrainingSlot(
        id = exerciseId,
        prescription = SlotPrescription(
            exerciseId = exerciseId,
            category = category,
            sortOrder = exerciseId.toInt(),
            supersetGroup = group,
            metricType = MetricType.REPS,
            setsMin = 3,
            setsMax = 3,
            repsLow = 10,
            repsHigh = 12,
            progressionRule = ProgressionRule.NONE,
        ),
    )
}
