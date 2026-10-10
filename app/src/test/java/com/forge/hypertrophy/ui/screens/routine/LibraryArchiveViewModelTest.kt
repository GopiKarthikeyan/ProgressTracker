package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.transfer.LibraryCatalogImporter
import com.forge.hypertrophy.data.transfer.LibraryCatalogProvider
import com.forge.hypertrophy.data.transfer.LibraryCatalogResult
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.ProgramJsonDefaults
import com.forge.hypertrophy.data.transfer.ProgramJsonHeader
import com.forge.hypertrophy.domain.model.Equipment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryArchiveViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val exerciseRepo = FakeExerciseRepository(clock)
    private val skillRepo = FakeSkillRepository(clock)
    private val catalog = EmptyLibraryCatalogProvider()
    private val catalogImporter = NoopLibraryCatalogImporter()
    private val activeViewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        activeViewModels.forEach { it.viewModelScope.cancel() }
        activeViewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun archivingAnExerciseSetsArchivedAtAndDropsItFromTheActiveList() = runBlocking {
        val id = exerciseRepo.insert(ExerciseEntity(name = "row", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        val viewModel = track(ExerciseLibraryViewModel(exerciseRepo, catalog, catalogImporter))
        awaitUntil { viewModel.uiState.value.rows.any { it.id == id } }

        viewModel.onEvent(ExerciseLibraryEvent.Archive(id))

        awaitUntil { viewModel.uiState.value.rows.none { it.id == id } }
        assertEquals(clock.instant(), exerciseRepo.get(id)!!.archivedAt)
        assertTrue(exerciseRepo.all().filter { it.archivedAt == null }.none { it.id == id })
    }

    @Test
    fun hardDeleteOfAReferencedExerciseSurfacesAnError() = runBlocking {
        val exerciseId = exerciseRepo.insert(ExerciseEntity(name = "row", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        exerciseRepo.referencedIdsSet.add(exerciseId)
        
        val viewModel = track(ExerciseLibraryViewModel(exerciseRepo, catalog, catalogImporter))
        awaitUntil {
            viewModel.uiState.value.rows.any { it.id == exerciseId && !it.canHardDelete }
        }

        viewModel.onEvent(ExerciseLibraryEvent.HardDelete(exerciseId))

        awaitUntil { viewModel.uiState.value.error == LibraryError.RESTRICTED }
        assertNotNull(exerciseRepo.get(exerciseId))
        assertFalse(viewModel.uiState.value.rows.single { it.id == exerciseId }.canHardDelete)
    }

    @Test
    fun archivingASkillSetsArchivedAtAndDropsItFromTheActiveList() = runBlocking {
        val id = skillRepo.insert(SkillEntity(name = "planche", archivedAt = null))
        val viewModel = track(SkillLibraryViewModel(skillRepo, catalog, catalogImporter))
        awaitUntil { viewModel.uiState.value.rows.any { it.id == id } }

        viewModel.onEvent(SkillLibraryEvent.Archive(id))

        awaitUntil { viewModel.uiState.value.rows.none { it.id == id } }
        assertEquals(clock.instant(), skillRepo.get(id)!!.archivedAt)
    }

    @Test
    fun hardDeleteOfAReferencedSkillSurfacesAnError() = runBlocking {
        val skillId = skillRepo.insert(SkillEntity(name = "planche", archivedAt = null))
        skillRepo.referencedIdsSet.add(skillId)
        
        val viewModel = track(SkillLibraryViewModel(skillRepo, catalog, catalogImporter))
        awaitUntil {
            viewModel.uiState.value.rows.any { it.id == skillId && !it.canHardDelete }
        }

        viewModel.onEvent(SkillLibraryEvent.HardDelete(skillId))

        awaitUntil { viewModel.uiState.value.error == LibraryError.RESTRICTED }
        assertNotNull(skillRepo.get(skillId))
    }

    private fun <T : ViewModel> track(model: T): T {
        activeViewModels.add(model)
        return model
    }

    private class EmptyLibraryCatalogProvider : LibraryCatalogProvider {
        override suspend fun catalog(): ProgramJson = ProgramJson(
            schemaVersion = 1,
            program = ProgramJsonHeader(name = "empty"),
            defaults = ProgramJsonDefaults(transitionRestSec = 120, barWeightKg = 20.0),
        )
    }

    private class NoopLibraryCatalogImporter : LibraryCatalogImporter {
        override suspend fun import(document: ProgramJson): LibraryCatalogResult =
            LibraryCatalogResult(addedExercises = 0, addedSkills = 0)
    }

    private class FakeExerciseRepository(private val clock: Clock) : ExerciseRepository {
        val exercises = mutableMapOf<Long, ExerciseEntity>()
        val referencedIdsSet = mutableSetOf<Long>()
        private val flow = MutableStateFlow(emptyList<ExerciseEntity>())

        override fun observeActive(): Flow<List<ExerciseEntity>> = flow.map { it.filter { e -> e.archivedAt == null } }
        override suspend fun get(id: Long): ExerciseEntity? = exercises[id]
        override suspend fun all(): List<ExerciseEntity> = exercises.values.toList()
        override suspend fun insert(exercise: ExerciseEntity): Long {
            val id = (exercises.keys.maxOrNull() ?: 0L) + 1
            exercises[id] = exercise.copy(id = id)
            flow.value = exercises.values.toList()
            return id
        }
        override suspend fun update(exercise: ExerciseEntity) {
            exercises[exercise.id] = exercise
            flow.value = exercises.values.toList()
        }
        override suspend fun archive(id: Long) {
            exercises[id] = exercises[id]!!.copy(archivedAt = clock.instant())
            flow.value = exercises.values.toList()
        }
        override suspend fun delete(id: Long) {
            if (id in referencedIdsSet) throw android.database.sqlite.SQLiteConstraintException("RESTRICTED")
            exercises.remove(id)
            flow.value = exercises.values.toList()
        }
        override suspend fun referencedIds(): Set<Long> = referencedIdsSet
    }

    private class FakeSkillRepository(private val clock: Clock) : SkillRepository {
        val skills = mutableMapOf<Long, SkillEntity>()
        val referencedIdsSet = mutableSetOf<Long>()
        private val flow = MutableStateFlow(emptyList<SkillEntity>())

        override fun observeActive(): Flow<List<SkillEntity>> = flow.map { it.filter { s -> s.archivedAt == null } }
        override suspend fun get(id: Long): SkillEntity? = skills[id]
        override suspend fun all(): List<SkillEntity> = skills.values.toList()
        override suspend fun insert(skill: SkillEntity): Long {
            val id = (skills.keys.maxOrNull() ?: 0L) + 1
            skills[id] = skill.copy(id = id)
            flow.value = skills.values.toList()
            return id
        }
        override suspend fun update(skill: SkillEntity) {
            skills[skill.id] = skill
            flow.value = skills.values.toList()
        }
        override suspend fun archive(id: Long) {
            skills[id] = skills[id]!!.copy(archivedAt = clock.instant())
            flow.value = skills.values.toList()
        }
        override suspend fun delete(id: Long) {
            if (id in referencedIdsSet) throw android.database.sqlite.SQLiteConstraintException("RESTRICTED")
            skills.remove(id)
            flow.value = skills.values.toList()
        }
        override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = MutableStateFlow(emptyList())
        override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = emptyList()
        override suspend fun insertStep(step: SkillStepEntity): Long = 0L
        override suspend fun updateStep(step: SkillStepEntity) {}
        override suspend fun deleteStep(id: Long) {}
        override suspend fun getProgress(skillId: Long): SkillProgressEntity? = null
        override suspend fun allSteps(): List<SkillStepEntity> = emptyList()
        override suspend fun allProgress(): List<SkillProgressEntity> = emptyList()
        override suspend fun upsertProgress(progress: SkillProgressEntity): Long = 0L
        override suspend fun recordStageEvent(event: com.forge.hypertrophy.data.entity.SkillStageEventEntity): Long = 0L
        override suspend fun stageEventsBetween(from: java.time.LocalDate, to: java.time.LocalDate): List<com.forge.hypertrophy.data.entity.SkillStageEventEntity> = emptyList()
        override suspend fun referencedIds(): Set<Long> = referencedIdsSet
    }
}
