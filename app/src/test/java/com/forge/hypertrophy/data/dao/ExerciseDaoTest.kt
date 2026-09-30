package com.forge.hypertrophy.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseDaoTest : DaoTest() {
    @Test
    fun insertThenGetAndObserveActiveInNameOrder() = runBlocking {
        val fixture = DaoFixture(db)
        val press = fixture.exercise("press")
        val curl = fixture.exercise("curl")

        assertEquals("curl", db.exerciseDao().get(curl)!!.name)
        assertEquals(listOf(curl, press), first(db.exerciseDao().observeActive()).map { it.id })
    }

    @Test
    fun updatePersistsLoadIncrement() = runBlocking {
        val id = DaoFixture(db).exercise("press")
        val stored = db.exerciseDao().get(id)!!

        db.exerciseDao().update(stored.copy(loadIncrementKg = 5.0, setupNotes = "belt"))

        val updated = db.exerciseDao().get(id)!!
        assertEquals(5.0, updated.loadIncrementKg, 0.0)
        assertEquals("belt", updated.setupNotes)
    }
}
