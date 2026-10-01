package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineReorderViewModelTest : ViewModelDaoTest() {
    @Test
    fun movingADayWritesContiguousIndices() = runBlocking {
        val fixture = DaoFixture(db)
        val programId = fixture.program()
        fixture.day(programId, sequenceIndex = 5, label = "c")
        fixture.day(programId, sequenceIndex = 9, label = "b")
        fixture.day(programId, sequenceIndex = 1, label = "a")
        val viewModel = track(
            ProgramEditorViewModel(
                SavedStateHandle(mapOf("programId" to programId)),
                programs(),
                routines(),
            ),
        )

        viewModel.onEvent(ProgramEditorEvent.MoveDay(from = 0, to = 1))

        awaitUntil {
            val days = db.routineDao().days(programId)
            days.map { it.sequenceIndex } == listOf(0, 1, 2) && days.map { it.label } == listOf("c", "a", "b")
        }
        viewModel.onEvent(ProgramEditorEvent.MoveDay(from = 1, to = 0))
        awaitUntil {
            val days = db.routineDao().days(programId)
            days.map { it.sequenceIndex } == listOf(0, 1, 2) && days.map { it.label } == listOf("a", "c", "b")
        }
        val days = db.routineDao().days(programId)
        assertEquals(listOf(0, 1, 2), days.map { it.sequenceIndex })
        assertEquals(days.size, days.map { it.sequenceIndex }.distinct().size)
    }

    @Test
    fun movingASlotWritesContiguousIndices() = runBlocking {
        val fixture = DaoFixture(db)
        val programId = fixture.program()
        val dayId = fixture.day(programId)
        val first = fixture.exercise("a")
        val second = fixture.exercise("b")
        val third = fixture.exercise("c")
        fixture.slot(dayId, third, sortOrder = 5)
        fixture.slot(dayId, second, sortOrder = 9)
        fixture.slot(dayId, first, sortOrder = 1)
        val viewModel = track(
            DayEditorViewModel(
                SavedStateHandle(mapOf("dayId" to dayId)),
                routines(),
                exercises(),
            ),
        )

        viewModel.onEvent(DayEditorEvent.MoveSlot(from = 0, to = 1))

        awaitUntil {
            val slots = db.routineDao().slots(dayId)
            slots.map { it.sortOrder } == listOf(0, 1, 2) &&
                slots.map { it.exerciseId } == listOf(third, first, second)
        }
        viewModel.onEvent(DayEditorEvent.MoveSlot(from = 1, to = 0))
        awaitUntil {
            val slots = db.routineDao().slots(dayId)
            slots.map { it.sortOrder } == listOf(0, 1, 2) &&
                slots.map { it.exerciseId } == listOf(first, third, second)
        }
        val slots = db.routineDao().slots(dayId)
        assertEquals(listOf(0, 1, 2), slots.map { it.sortOrder })
        assertEquals(slots.size, slots.map { it.sortOrder }.distinct().size)
    }

    private fun programs(): ProgramRepository = RoomProgramRepository(db.programDao())

    private fun routines(): RoutineRepository = RoomRoutineRepository(db.routineDao())

    private fun exercises(): ExerciseRepository = RoomExerciseRepository(
        db.exerciseDao(),
        db.routineDao(),
        db.sessionDao(),
        db.mediaDao(),
        Clock.systemUTC(),
    )
}
