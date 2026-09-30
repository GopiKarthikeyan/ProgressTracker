package com.forge.hypertrophy.data.dao

import com.forge.hypertrophy.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDaoTest : DaoTest() {
    @Test
    fun inProgressSessionWithSlotAndSet() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId)
        val sessionId = fixture.session(dayId = dayId, status = SessionStatus.IN_PROGRESS)
        val slot = db.routineDao().getSlot(routineSlotId)!!
        val sessionSlotId = fixture.sessionSlot(sessionId, routineSlotId, fixture.prescription(slot))
        val setId = fixture.setEntry(sessionSlotId)

        assertEquals(sessionId, first(db.sessionDao().observeInProgress()).single().id)
        assertEquals(sessionId, db.sessionDao().get(sessionId)!!.id)
        assertEquals(sessionSlotId, first(db.sessionDao().observeSlots(sessionId)).single().id)
        assertEquals(setId, first(db.sessionDao().observeSets(sessionSlotId)).single().id)
    }

    @Test
    fun updatePersistsStatus() = runBlocking {
        val id = DaoFixture(db).session(status = SessionStatus.PLANNED)
        val stored = db.sessionDao().get(id)!!

        db.sessionDao().update(stored.copy(status = SessionStatus.COMPLETED))

        assertEquals(SessionStatus.COMPLETED, db.sessionDao().get(id)!!.status)
        assertTrue(first(db.sessionDao().observeInProgress()).isEmpty())
    }

    @Test
    fun prescriptionSnapshotIgnoresLaterRoutineEdit() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId, setsMax = 8)
        val logged = db.routineDao().getSlot(routineSlotId)!!
        val sessionId = fixture.session(dayId = dayId)
        fixture.sessionSlot(sessionId, routineSlotId, fixture.prescription(logged))

        db.routineDao().updateSlot(logged.copy(setsMax = 12))

        val snapshot = first(db.sessionDao().observeSlots(sessionId)).single().prescriptionSnapshot
        assertEquals(8, snapshot.setsMax)
        assertEquals(12, db.routineDao().getSlot(routineSlotId)!!.setsMax)
    }
}
