package com.forge.hypertrophy.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioDaoTest : DaoTest() {
    @Test
    fun trackPointsAreObservedInSequenceOrder() = runBlocking {
        val fixture = DaoFixture(db)
        val sessionId = fixture.session()
        val logId = fixture.cardioLog(sessionId)
        val later = fixture.trackPoint(logId, sequenceIndex = 2)
        val earlier = fixture.trackPoint(logId, sequenceIndex = 0)

        assertEquals(logId, first(db.cardioDao().observeLog(sessionId))!!.id)
        assertEquals(
            listOf(earlier, later),
            first(db.cardioDao().observeTrackPoints(logId)).map { it.id },
        )
    }

    @Test
    fun updatePersistsDistance() = runBlocking {
        val fixture = DaoFixture(db)
        val sessionId = fixture.session()
        val logId = fixture.cardioLog(sessionId)
        val stored = first(db.cardioDao().observeLog(sessionId))!!

        db.cardioDao().update(stored.copy(distanceM = 2500.0))

        val updated = first(db.cardioDao().observeLog(sessionId))!!
        assertEquals(logId, updated.id)
        assertEquals(2500.0, updated.distanceM, 0.0)
    }
}
