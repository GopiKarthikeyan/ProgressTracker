package com.forge.hypertrophy.data.dao

import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.domain.model.CardioStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineDaoTest : DaoTest() {
    @Test
    fun dayTreeRoundTrip() = runBlocking {
        val fixture = DaoFixture(db)
        val programId = fixture.program()
        val secondDay = fixture.day(programId, sequenceIndex = 1, label = "pull")
        val firstDay = fixture.day(programId, sequenceIndex = 0, label = "push")
        val checklistId = fixture.checklist(firstDay)
        val exerciseId = fixture.exercise("press")
        val slotId = fixture.slot(firstDay, exerciseId, sortOrder = 1)
        val earlierSlot = fixture.slot(firstDay, exerciseId, sortOrder = 0)
        val alternateId = fixture.exercise("curl")
        val alternativeId = fixture.alternative(slotId, alternateId)

        assertEquals(listOf(firstDay, secondDay), first(db.routineDao().observeDays(programId)).map { it.id })
        assertEquals("push", db.routineDao().getDay(firstDay)!!.label)
        assertEquals(checklistId, first(db.routineDao().observeChecklist(firstDay)).single().id)
        assertEquals(listOf(earlierSlot, slotId), first(db.routineDao().observeSlots(firstDay)).map { it.id })
        assertEquals(alternativeId, first(db.routineDao().observeAlternatives(slotId)).single().id)
    }

    @Test
    fun updateDayPersistsLabel() = runBlocking {
        val fixture = DaoFixture(db)
        val dayId = fixture.day(fixture.program(), label = "push")
        val stored = db.routineDao().getDay(dayId)!!

        db.routineDao().updateDay(stored.copy(label = "legs"))

        assertEquals("legs", db.routineDao().getDay(dayId)!!.label)
    }

    @Test
    fun cardioPlanUpsertReplacesThePlanForThatDay() = runBlocking {
        val fixture = DaoFixture(db)
        val dayId = fixture.day(fixture.program())
        fixture.cardioPlan(dayId)

        db.routineDao().upsertCardioPlan(
            CardioPlanEntity(
                dayId = dayId,
                type = CardioStyle.WALK,
                targetDistanceM = 1500,
                isOptional = true,
            ),
        )

        val plan = first(db.routineDao().observeCardioPlan(dayId))
        assertEquals(CardioStyle.WALK, plan!!.type)
        assertEquals(1500, plan.targetDistanceM)
        assertEquals(true, plan.isOptional)
        assertNull(first(db.routineDao().observeCardioPlan(dayId + 1)))
    }
}
