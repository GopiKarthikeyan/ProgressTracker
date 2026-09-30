package com.forge.hypertrophy.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineReorderTest : DaoTest() {
    @Test
    fun reorderDaysWritesZeroThroughNWithoutGapsOrDuplicates() = runBlocking {
        val fixture = DaoFixture(db)
        val programId = fixture.program()
        val first = fixture.day(programId, sequenceIndex = 5, label = "a")
        val second = fixture.day(programId, sequenceIndex = 9, label = "b")
        val third = fixture.day(programId, sequenceIndex = 1, label = "c")

        db.routineDao().reorderDays(programId, listOf(third, first, second))

        val days = db.routineDao().days(programId)
        assertEquals(listOf("c", "a", "b"), days.map { it.label })
        assertEquals(listOf(0, 1, 2), days.map { it.sequenceIndex })
        assertEquals(days.size, days.map { it.sequenceIndex }.distinct().size)
    }

    @Test
    fun reorderSlotsWritesZeroThroughNWithoutGapsOrDuplicates() = runBlocking {
        val fixture = DaoFixture(db)
        val dayId = fixture.day(fixture.program())
        val exerciseId = fixture.exercise("press")
        val first = fixture.slot(dayId, exerciseId, sortOrder = 4)
        val second = fixture.slot(dayId, exerciseId, sortOrder = 8)
        val third = fixture.slot(dayId, exerciseId, sortOrder = 2)

        db.routineDao().reorderSlots(dayId, listOf(second, third, first))

        val slots = db.routineDao().slots(dayId)
        assertEquals(listOf(second, third, first), slots.map { it.id })
        assertEquals(listOf(0, 1, 2), slots.map { it.sortOrder })
        assertEquals(slots.size, slots.map { it.sortOrder }.distinct().size)
    }
}
