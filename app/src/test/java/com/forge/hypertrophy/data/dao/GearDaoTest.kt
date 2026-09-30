package com.forge.hypertrophy.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GearDaoTest : DaoTest() {
    @Test
    fun insertThenGetAndObserveActiveInNameOrder() = runBlocking {
        val fixture = DaoFixture(db)
        val shoes = fixture.gear("shoes")
        val belt = fixture.gear("belt")

        assertEquals("belt", db.gearDao().get(belt)!!.name)
        assertEquals(listOf(belt, shoes), first(db.gearDao().observeActive()).map { it.id })
    }

    @Test
    fun updatePersistsMileageLimit() = runBlocking {
        val id = DaoFixture(db).gear("shoes")
        val stored = db.gearDao().get(id)!!

        db.gearDao().update(stored.copy(mileageLimitM = 800_000))

        assertEquals(800_000, db.gearDao().get(id)!!.mileageLimitM)
    }
}
