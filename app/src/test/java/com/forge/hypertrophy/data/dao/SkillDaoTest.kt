package com.forge.hypertrophy.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillDaoTest : DaoTest() {
    @Test
    fun stepsAreObservedInSortOrder() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("hold")
        val later = fixture.step(skillId, sortOrder = 2)
        val earlier = fixture.step(skillId, sortOrder = 1)

        assertEquals(listOf(earlier, later), first(db.skillDao().observeSteps(skillId)).map { it.id })
        assertEquals("hold", db.skillDao().get(skillId)!!.name)
    }

    @Test
    fun upsertProgressReplacesTheRowForThatSkill() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("hold")
        val stepId = fixture.step(skillId)
        fixture.progress(skillId, stepId, stage = 1)

        fixture.progress(skillId, stepId, stage = 2)

        assertEquals(2, db.skillDao().getProgress(skillId)!!.stage)
    }

    @Test
    fun updatePersistsName() = runBlocking {
        val id = DaoFixture(db).skill("hold")
        val stored = db.skillDao().get(id)!!

        db.skillDao().update(stored.copy(name = "tuck"))

        assertEquals("tuck", db.skillDao().get(id)!!.name)
    }
}
