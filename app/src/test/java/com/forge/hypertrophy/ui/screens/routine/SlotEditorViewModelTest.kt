package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotEditorViewModelTest : ViewModelDaoTest() {
    @Test
    fun setsMinAboveMaxIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = db.routineDao().getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.SetsMin(9))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.SETS, viewModel.uiState.value.validationError)
        assertEquals(before, db.routineDao().getSlot(slotId))
    }

    @Test
    fun repsLowAboveHighIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = db.routineDao().getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.RepsLow(12))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.REPS, viewModel.uiState.value.validationError)
        assertEquals(before, db.routineDao().getSlot(slotId))
    }

    @Test
    fun restMinAboveMaxIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = db.routineDao().getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.RestMin(200))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.REST, viewModel.uiState.value.validationError)
        assertEquals(before, db.routineDao().getSlot(slotId))
    }

    @Test
    fun holdTargetAboveMaxIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = db.routineDao().getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.HoldTargetMax(10))
        viewModel.onEvent(SlotEditorEvent.HoldTarget(20))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.HOLD, viewModel.uiState.value.validationError)
        assertEquals(before, db.routineDao().getSlot(slotId))
    }

    @Test
    fun pairingWithTheNeighbourSharesAGroupAndUnpairingClearsIt() = runBlocking {
        val fixture = DaoFixture(db)
        val dayId = fixture.day(fixture.program())
        val currentId = fixture.slot(dayId, fixture.exercise("a"), sortOrder = 0)
        val neighbourId = fixture.slot(dayId, fixture.exercise("b"), sortOrder = 1)
        val viewModel = editor(currentId)
        awaitUntil { viewModel.uiState.value.neighbours.singleOrNull()?.id == neighbourId }

        viewModel.onEvent(SlotEditorEvent.PairWith(neighbourId))
        awaitUntil { viewModel.uiState.value.paired }

        val paired = db.routineDao().slots(dayId)
        val group = paired.first().supersetGroup
        assertNotNull(group)
        assertEquals(listOf(group, group), paired.map { it.supersetGroup })
        assertTrue(viewModel.uiState.value.neighbours.single().sameGroup)

        viewModel.onEvent(SlotEditorEvent.PairWith(neighbourId))
        awaitUntil { viewModel.uiState.value.supersetGroup == null && !viewModel.uiState.value.paired }

        assertTrue(db.routineDao().slots(dayId).all { it.supersetGroup == null })
    }

    @Test
    fun savingASlotLeavesTheSessionSnapshotAlone() = runBlocking {
        val fixture = DaoFixture(db)
        val dayId = fixture.day(fixture.program())
        val slotId = fixture.slot(dayId, fixture.exercise("a"))
        val slot = db.routineDao().getSlot(slotId)!!
        val sessionId = fixture.session(dayId)
        fixture.sessionSlot(sessionId, slotId, fixture.prescription(slot))
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }

        viewModel.onEvent(SlotEditorEvent.SetsMax(slot.setsMax + 1))
        viewModel.onEvent(SlotEditorEvent.Save)

        awaitUntil { db.routineDao().getSlot(slotId)!!.setsMax == slot.setsMax + 1 }
        assertEquals(slot.setsMax, db.sessionDao().allSlots().single().prescriptionSnapshot.setsMax)
        assertNull(viewModel.uiState.value.validationError)
    }

    private suspend fun seedSlot(): Long {
        val fixture = DaoFixture(db)
        return fixture.slot(fixture.day(fixture.program()), fixture.exercise("a"))
    }

    private fun editor(slotId: Long) = track(
        SlotEditorViewModel(
            SavedStateHandle(mapOf("slotId" to slotId)),
            RoomRoutineRepository(db.routineDao()),
            RoomExerciseRepository(
                db.exerciseDao(),
                db.routineDao(),
                db.sessionDao(),
                db.mediaDao(),
                Clock.systemUTC(),
            ),
            RoomSkillRepository(
                db.skillDao(),
                db.exerciseDao(),
                db.routineDao(),
                db.sessionDao(),
                Clock.systemUTC(),
            ),
        ),
    )
}
