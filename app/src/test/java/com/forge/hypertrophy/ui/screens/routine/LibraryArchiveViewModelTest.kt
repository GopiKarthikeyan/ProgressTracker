package com.forge.hypertrophy.ui.screens.routine

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArchiveViewModelTest : ViewModelDaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun archivingAnExerciseSetsArchivedAtAndDropsItFromTheActiveList() = runBlocking {
        val id = DaoFixture(db).exercise("row")
        val viewModel = track(ExerciseLibraryViewModel(exercises()))
        awaitUntil { viewModel.uiState.value.rows.any { it.id == id } }

        viewModel.onEvent(ExerciseLibraryEvent.Archive(id))

        awaitUntil { viewModel.uiState.value.rows.none { it.id == id } }
        assertEquals(clock.instant(), db.exerciseDao().get(id)!!.archivedAt)
        assertTrue(first(db.exerciseDao().observeActive()).none { it.id == id })
    }

    @Test
    fun hardDeleteOfAReferencedExerciseSurfacesAnError() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("row")
        fixture.slot(fixture.day(fixture.program()), exerciseId)
        val viewModel = track(ExerciseLibraryViewModel(exercises()))
        awaitUntil {
            viewModel.uiState.value.rows.any { it.id == exerciseId && !it.canHardDelete }
        }

        viewModel.onEvent(ExerciseLibraryEvent.HardDelete(exerciseId))

        awaitUntil { viewModel.uiState.value.error == LibraryError.RESTRICTED }
        assertNotNull(db.exerciseDao().get(exerciseId))
        assertFalse(viewModel.uiState.value.rows.single { it.id == exerciseId }.canHardDelete)
    }

    @Test
    fun archivingASkillSetsArchivedAtAndDropsItFromTheActiveList() = runBlocking {
        val id = DaoFixture(db).skill("planche")
        val viewModel = track(SkillLibraryViewModel(skills()))
        awaitUntil { viewModel.uiState.value.rows.any { it.id == id } }

        viewModel.onEvent(SkillLibraryEvent.Archive(id))

        awaitUntil { viewModel.uiState.value.rows.none { it.id == id } }
        assertEquals(clock.instant(), db.skillDao().get(id)!!.archivedAt)
    }

    @Test
    fun hardDeleteOfAReferencedSkillSurfacesAnError() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("planche")
        fixture.exercise("row", skillId = skillId)
        val viewModel = track(SkillLibraryViewModel(skills()))
        awaitUntil {
            viewModel.uiState.value.rows.any { it.id == skillId && !it.canHardDelete }
        }

        viewModel.onEvent(SkillLibraryEvent.HardDelete(skillId))

        awaitUntil { viewModel.uiState.value.error == LibraryError.RESTRICTED }
        assertNotNull(db.skillDao().get(skillId))
    }

    private fun exercises() = RoomExerciseRepository(
        db.exerciseDao(),
        db.routineDao(),
        db.sessionDao(),
        db.mediaDao(),
        clock,
    )

    private fun skills() = RoomSkillRepository(
        db.skillDao(),
        db.exerciseDao(),
        db.routineDao(),
        db.sessionDao(),
        clock,
    )
}
