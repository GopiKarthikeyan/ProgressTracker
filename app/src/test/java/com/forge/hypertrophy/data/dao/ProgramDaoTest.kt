package com.forge.hypertrophy.data.dao

import com.forge.hypertrophy.domain.model.ScheduleMode
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramDaoTest : DaoTest() {
    @Test
    fun insertThenGetAndObserve() = runBlocking {
        val id = DaoFixture(db).program()

        val stored = db.programDao().get()
        assertEquals(id, stored!!.id)
        assertEquals("block", stored.name)
        assertEquals(stored, first(db.programDao().observe()))
    }

    @Test
    fun updatePersistsSchedule() = runBlocking {
        val id = DaoFixture(db).program()
        val stored = db.programDao().get()!!

        db.programDao().update(
            stored.copy(
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 3,
                deloadActive = true,
                deloadStartedOn = LocalDate.of(2026, 4, 2),
            ),
        )

        val updated = db.programDao().get()
        assertEquals(id, updated!!.id)
        assertEquals(ScheduleMode.FIXED, updated.scheduleMode)
        assertEquals(3, updated.rollingSequence)
        assertEquals(true, updated.deloadActive)
        assertEquals(LocalDate.of(2026, 4, 2), updated.deloadStartedOn)
        assertEquals(updated, first(db.programDao().observe()))
    }
}
