package com.forge.hypertrophy.domain.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillProgressSummaryTest {
    private val steps = listOf(
        SkillStepTargets(1, "Tuck", stage1TotalSec = 12, stage2TotalHighSec = 18, stage3UnbrokenSec = 10),
        SkillStepTargets(2, "Advanced Tuck", stage1TotalSec = 12, stage2TotalHighSec = 18, stage3UnbrokenSec = 10),
        SkillStepTargets(3, "Full", stage1TotalSec = 15, stage2TotalHighSec = 20, stage3UnbrokenSec = 12),
    )

    @Test
    fun missingProgressStartsAtFirstStepStageOne() {
        val summary = skillProgressSummary(steps, progress = null)!!
        assertEquals(0, summary.stepIndex)
        assertEquals(3, summary.stepCount)
        assertEquals("Tuck", summary.stepName)
        assertEquals(1, summary.stage)
        assertEquals(12, summary.targetSec)
        assertEquals(SkillHoldTargetKind.TOTAL, summary.targetKind)
    }

    @Test
    fun stageTwoUsesHighTotal() {
        val summary = skillProgressSummary(steps, SkillProgressRef(1, stage = 2))!!
        assertEquals(0, summary.stepIndex)
        assertEquals(2, summary.stage)
        assertEquals(18, summary.targetSec)
        assertEquals(SkillHoldTargetKind.TOTAL, summary.targetKind)
    }

    @Test
    fun stageThreeUsesUnbrokenHold() {
        val summary = skillProgressSummary(steps, SkillProgressRef(2, stage = 3))!!
        assertEquals(1, summary.stepIndex)
        assertEquals("Advanced Tuck", summary.stepName)
        assertEquals(3, summary.stage)
        assertEquals(10, summary.targetSec)
        assertEquals(SkillHoldTargetKind.UNBROKEN, summary.targetKind)
    }

    @Test
    fun lastStepIsReachable() {
        val summary = skillProgressSummary(steps, SkillProgressRef(3, stage = 1))!!
        assertEquals(2, summary.stepIndex)
        assertEquals("Full", summary.stepName)
        assertEquals(15, summary.targetSec)
    }

    @Test
    fun emptyStepsReturnsNull() {
        assertNull(skillProgressSummary(emptyList(), progress = null))
    }
}
