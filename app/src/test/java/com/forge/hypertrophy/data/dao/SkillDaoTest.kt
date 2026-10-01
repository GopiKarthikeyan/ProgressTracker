package com.forge.hypertrophy.data.dao

import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import java.time.Instant
import java.time.LocalDate
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
    fun stageEventsAreReadByDateRangeAndGoWithTheirSkill() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("planche")
        val other = fixture.skill("lever")
        val recordedAt = Instant.parse("2026-10-01T10:00:00Z")
        db.skillDao().insertStageEvent(
            SkillStageEventEntity(skillId = skillId, date = LocalDate.of(2026, 9, 29), fromTier = 0, fromStage = 1, toTier = 0, toStage = 2, recordedAt = recordedAt),
        )
        db.skillDao().insertStageEvent(
            SkillStageEventEntity(skillId = other, date = LocalDate.of(2026, 10, 1), fromTier = 1, fromStage = 3, toTier = 2, toStage = 1, recordedAt = recordedAt),
        )
        db.skillDao().insertStageEvent(
            SkillStageEventEntity(skillId = skillId, date = LocalDate.of(2026, 10, 6), fromTier = 0, fromStage = 2, toTier = 0, toStage = 3, recordedAt = recordedAt),
        )

        val week = db.skillDao().stageEventsBetween(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4))
        assertEquals(listOf(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1)), week.map { it.date })
        assertEquals(2, week.last().toTier)

        db.skillDao().delete(other)

        val remaining = db.skillDao().stageEventsBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertEquals(listOf(skillId, skillId), remaining.map { it.skillId })
    }

    @Test
    fun updatePersistsName() = runBlocking {
        val id = DaoFixture(db).skill("hold")
        val stored = db.skillDao().get(id)!!

        db.skillDao().update(stored.copy(name = "tuck"))

        assertEquals("tuck", db.skillDao().get(id)!!.name)
    }
}
