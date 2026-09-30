package com.forge.hypertrophy.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDaoTest : DaoTest() {
    @Test
    fun observeByExerciseAndBySet() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val routineSlotId = fixture.slot(dayId, exerciseId)
        val sessionId = fixture.session(dayId = dayId)
        val slot = db.routineDao().getSlot(routineSlotId)!!
        val sessionSlotId = fixture.sessionSlot(sessionId, routineSlotId, fixture.prescription(slot))
        val setId = fixture.setEntry(sessionSlotId)
        val mediaId = fixture.media(exerciseId, setId)

        assertEquals(mediaId, first(db.mediaDao().observeForExercise(exerciseId)).single().id)
        assertEquals(mediaId, first(db.mediaDao().observeForSet(setId)).single().id)
    }

    @Test
    fun updatePersistsUri() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val mediaId = fixture.media(exerciseId, setEntryId = null)
        val stored = first(db.mediaDao().observeForExercise(exerciseId)).single()

        db.mediaDao().update(stored.copy(uri = "content://media/2"))

        assertEquals("content://media/2", first(db.mediaDao().observeForExercise(exerciseId)).single().uri)
        assertEquals(mediaId, first(db.mediaDao().observeForExercise(exerciseId)).single().id)
    }
}
