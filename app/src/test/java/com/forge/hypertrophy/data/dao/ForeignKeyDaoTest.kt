package com.forge.hypertrophy.data.dao

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ForeignKeyDaoTest : DaoTest() {
    @Test
    fun foreignKeysPragmaIsOn() {
        val db = this.db.openHelper.writableDatabase
        db.query("PRAGMA foreign_keys").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun deleteSessionRemovesSlotsAndSets() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId)
        val sessionId = fixture.session(dayId = dayId)
        val slot = db.routineDao().getSlot(routineSlotId)!!
        val sessionSlotId = fixture.sessionSlot(sessionId, routineSlotId, fixture.prescription(slot))
        fixture.setEntry(sessionSlotId)

        db.sessionDao().delete(sessionId)

        assertNull(db.sessionDao().get(sessionId))
        assertTrue(first(db.sessionDao().observeSlots(sessionId)).isEmpty())
        assertTrue(first(db.sessionDao().observeSets(sessionSlotId)).isEmpty())
    }

    @Test
    fun deleteSessionRemovesCardioLogAndTrackPoints() = runBlocking {
        val fixture = DaoFixture(db)
        val sessionId = fixture.session()
        val logId = fixture.cardioLog(sessionId)
        fixture.trackPoint(logId, sequenceIndex = 0)

        db.sessionDao().delete(sessionId)

        assertNull(first(db.cardioDao().observeLog(sessionId)))
        assertTrue(first(db.cardioDao().observeTrackPoints(logId)).isEmpty())
    }

    @Test
    fun deleteSkillRemovesStepsAndProgress() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("hold")
        val stepId = fixture.step(skillId)
        fixture.progress(skillId, stepId)

        db.skillDao().delete(skillId)

        assertNull(db.skillDao().get(skillId))
        assertTrue(first(db.skillDao().observeSteps(skillId)).isEmpty())
        assertNull(db.skillDao().getProgress(skillId))
    }

    @Test
    fun deleteRoutineDayNullsSessionDayId() = runBlocking {
        val fixture = DaoFixture(db)
        val dayId = fixture.day(fixture.program())
        val sessionId = fixture.session(dayId = dayId)

        db.routineDao().deleteDay(dayId)

        assertNull(db.sessionDao().get(sessionId)!!.dayId)
    }

    @Test
    fun deleteRoutineSlotNullsSessionSlotId() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId)
        val sessionId = fixture.session()
        val slot = db.routineDao().getSlot(routineSlotId)!!
        fixture.sessionSlot(sessionId, routineSlotId, fixture.prescription(slot))

        db.routineDao().deleteSlot(routineSlotId)

        assertNull(first(db.sessionDao().observeSlots(sessionId)).single().slotId)
    }

    @Test
    fun deleteSetNullsMediaSetEntryId() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId)
        val sessionId = fixture.session()
        val slot = db.routineDao().getSlot(routineSlotId)!!
        val sessionSlotId = fixture.sessionSlot(sessionId, routineSlotId, fixture.prescription(slot))
        val setId = fixture.setEntry(sessionSlotId)
        fixture.media(exerciseId, setId)

        db.sessionDao().deleteSet(setId)

        val media = first(db.mediaDao().observeForExercise(exerciseId)).single()
        assertNull(media.setEntryId)
        assertTrue(first(db.mediaDao().observeForSet(setId)).isEmpty())
    }

    @Test
    fun deleteSkillStepNullsRoutineSlotTarget() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("hold")
        val stepId = fixture.step(skillId)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId, targetSkillStepId = stepId)

        db.skillDao().deleteStep(stepId)

        assertNull(db.routineDao().getSlot(routineSlotId)!!.targetSkillStepId)
    }

    @Test
    fun deleteReferencedExerciseThrows() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        fixture.slot(dayId, exerciseId)

        assertConstraint {
            db.exerciseDao().delete(exerciseId)
        }
        assertEquals(exerciseId, db.exerciseDao().get(exerciseId)!!.id)
    }

    @Test
    fun deleteReferencedSkillThrows() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("hold")
        fixture.exercise("press", skillId = skillId)

        assertConstraint {
            db.skillDao().delete(skillId)
        }
        assertEquals(skillId, db.skillDao().get(skillId)!!.id)
    }

    @Test
    fun deleteReferencedGearThrows() = runBlocking {
        val fixture = DaoFixture(db)
        val gearId = fixture.gear("shoes")
        fixture.cardioLog(fixture.session(), gearId = gearId)

        assertConstraint {
            db.gearDao().delete(gearId)
        }
        assertEquals(gearId, db.gearDao().get(gearId)!!.id)
    }

    @Test
    fun deleteUnreferencedArchivedExercise() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        db.exerciseDao().archive(exerciseId, DaoFixture.ARCHIVED_AT)

        db.exerciseDao().delete(exerciseId)

        assertNull(db.exerciseDao().get(exerciseId))
    }

    @Test
    fun deleteUnreferencedArchivedSkill() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("hold")
        db.skillDao().archive(skillId, DaoFixture.ARCHIVED_AT)

        db.skillDao().delete(skillId)

        assertNull(db.skillDao().get(skillId))
    }

    @Test
    fun deleteUnreferencedArchivedGear() = runBlocking {
        val fixture = DaoFixture(db)
        val gearId = fixture.gear("shoes")
        db.gearDao().archive(gearId, DaoFixture.ARCHIVED_AT)

        db.gearDao().delete(gearId)

        assertNull(db.gearDao().get(gearId))
    }

    @Test
    fun archiveHidesExerciseSkillAndGearFromActiveLists() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val skillId = fixture.skill("hold")
        val gearId = fixture.gear("shoes")

        db.exerciseDao().archive(exerciseId, DaoFixture.ARCHIVED_AT)
        db.skillDao().archive(skillId, DaoFixture.ARCHIVED_AT)
        db.gearDao().archive(gearId, DaoFixture.ARCHIVED_AT)

        assertEquals(DaoFixture.ARCHIVED_AT, db.exerciseDao().get(exerciseId)!!.archivedAt)
        assertEquals(DaoFixture.ARCHIVED_AT, db.skillDao().get(skillId)!!.archivedAt)
        assertEquals(DaoFixture.ARCHIVED_AT, db.gearDao().get(gearId)!!.archivedAt)
        assertTrue(first(db.exerciseDao().observeActive()).isEmpty())
        assertTrue(first(db.skillDao().observeActive()).isEmpty())
        assertTrue(first(db.gearDao().observeActive()).isEmpty())
    }

    private suspend fun assertConstraint(block: suspend () -> Unit) {
        try {
            block()
            fail("SQLiteConstraintException expected")
        } catch (_: SQLiteConstraintException) {
        }
    }
}
