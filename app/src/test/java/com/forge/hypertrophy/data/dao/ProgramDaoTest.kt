package com.forge.hypertrophy.data.dao

import com.forge.hypertrophy.domain.model.ScheduleMode
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun deletingTheActiveProgramPromotesTheNextHigherId() = runBlocking {
        val fixture = DaoFixture(db)
        val lower = fixture.program("lower")
        val higher = fixture.program("higher")
        db.programDao().setActive(lower)

        db.programDao().delete(lower)

        val active = first(db.programDao().observeActive())
        assertEquals(higher, active!!.id)
        assertEquals("higher", active.name)
        assertTrue(active.isActive)
        assertEquals(listOf(higher), first(db.programDao().observeAll()).map { it.id })
    }

    @Test
    fun deletingTheHighestActiveProgramPromotesTheLowestRemainingId() = runBlocking {
        val fixture = DaoFixture(db)
        val lower = fixture.program("lower")
        val higher = fixture.program("higher")
        db.programDao().setActive(higher)

        db.programDao().delete(higher)

        assertEquals(lower, first(db.programDao().observeActive())!!.id)
        assertEquals(listOf(lower), first(db.programDao().observeAll()).map { it.id })
    }

    @Test
    fun deletingTheOnlyProgramLeavesNoActiveProgram() = runBlocking {
        val only = DaoFixture(db).program("only")
        db.programDao().setActive(only)

        db.programDao().delete(only)

        assertNull(first(db.programDao().observeActive()))
        assertTrue(first(db.programDao().observeAll()).isEmpty())
    }

    @Test
    fun deletingAnInactiveProgramKeepsTheActiveProgram() = runBlocking {
        val fixture = DaoFixture(db)
        val active = fixture.program("active")
        val inactive = fixture.program("inactive")
        db.programDao().setActive(active)

        db.programDao().delete(inactive)

        assertEquals(active, first(db.programDao().observeActive())!!.id)
        assertEquals(listOf(active), first(db.programDao().observeAll()).map { it.id })
    }
}
