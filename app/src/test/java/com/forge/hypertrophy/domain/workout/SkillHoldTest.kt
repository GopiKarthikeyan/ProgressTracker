package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.SkillStageTargets
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillHoldTest {
    private val targets = SkillStageTargets(
        stage1TotalSec = 12,
        stage2TotalLowSec = 15,
        stage2TotalHighSec = 18,
        stage3UnbrokenSec = 10,
    )

    @Test
    fun stage1SuggestsWhatIsLeftOfTheTotal() {
        assertEquals(12, skillHoldSeconds(1, targets, loggedHoldSec = 0))
        assertEquals(7, skillHoldSeconds(1, targets, loggedHoldSec = 5))
    }

    @Test
    fun stage2AdvancesAtTheTopOfTheRange() {
        assertEquals(18, skillHoldSeconds(2, targets, loggedHoldSec = 0))
        assertEquals(2, skillHoldSeconds(2, targets, loggedHoldSec = 16))
    }

    @Test
    fun aMetTotalSuggestsTheFullTargetOnTheNextSet() {
        assertEquals(12, skillHoldSeconds(1, targets, loggedHoldSec = 12))
        assertEquals(18, skillHoldSeconds(2, targets, loggedHoldSec = 20))
    }

    @Test
    fun stage3SuggestsOneUnbrokenHold() {
        assertEquals(10, skillHoldSeconds(3, targets, loggedHoldSec = 0))
        assertEquals(10, skillHoldSeconds(3, targets, loggedHoldSec = 10))
    }
}
