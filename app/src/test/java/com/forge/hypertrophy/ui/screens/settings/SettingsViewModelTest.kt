package com.forge.hypertrophy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.backup.BackupClient
import com.forge.hypertrophy.data.backup.BackupManifest
import com.forge.hypertrophy.data.backup.BackupProgress
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.repository.MediaPreferencesRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val programRepo = FakeProgramRepository()
    private val routineRepo = FakeRoutineRepository()
    private val activeViewModels = mutableListOf<SettingsViewModel>()

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
    fun duplicateWeekdayMappingIsRejectedAndNamesTheDays() = runBlocking {
        val programId = fixedProgram()
        val first = insertDay(programId, "push", weekday = 1, sequence = 0)
        val second = insertDay(programId, "pull", weekday = 2, sequence = 1)
        val viewModel = settings(FakePreferences(), sample(false))
        awaitUntil { viewModel.uiState.value.days.size == 2 }

        viewModel.onEvent(SettingsEvent.Weekday(second, 1))
        viewModel.onEvent(SettingsEvent.SaveWeekdays)

        assertEquals(
            listOf(WeekdayConflict(weekday = 1, dayLabels = listOf("push", "pull"))),
            viewModel.uiState.value.weekdayConflicts,
        )
        assertEquals(1, routineRepo.getDay(first)!!.dayOfWeek)
        assertEquals(2, routineRepo.getDay(second)!!.dayOfWeek)
    }

    @Test
    fun validWeekdayMappingSaves() = runBlocking {
        val programId = fixedProgram()
        insertDay(programId, "push", weekday = 1, sequence = 0)
        val second = insertDay(programId, "pull", weekday = 2, sequence = 1)
        val viewModel = settings(FakePreferences(), sample(false))
        awaitUntil { viewModel.uiState.value.days.size == 2 }

        viewModel.onEvent(SettingsEvent.Weekday(second, 4))
        viewModel.onEvent(SettingsEvent.SaveWeekdays)

        awaitUntil { routineRepo.getDay(second)!!.dayOfWeek == 4 }
        assertTrue(viewModel.uiState.value.weekdayConflicts.isEmpty())
        awaitUntil { !viewModel.uiState.value.weekdaysDirty }
    }

    @Test
    fun plateInventoryAndTransitionRestRoundTrip() = runBlocking {
        val preferences = FakePreferences()
        val viewModel = settings(preferences, sample(false))
        awaitUntil { viewModel.uiState.value.transitionRestSeconds == 120 }

        viewModel.onEvent(SettingsEvent.PlateDraft("20"))
        viewModel.onEvent(SettingsEvent.AddPlate)
        viewModel.onEvent(SettingsEvent.PlateDraft("2.5"))
        viewModel.onEvent(SettingsEvent.AddPlate)
        viewModel.onEvent(SettingsEvent.TransitionRest(135))

        awaitUntil { viewModel.uiState.value.platesKg == listOf(20.0, 2.5) }
        awaitUntil { viewModel.uiState.value.transitionRestSeconds == 135 }
        assertEquals(listOf(20.0, 2.5), preferences.plateInventoryKg.value)
        assertEquals(135, preferences.transitionRestSeconds.value)

        viewModel.onEvent(SettingsEvent.RemovePlate(20.0))
        awaitUntil { viewModel.uiState.value.platesKg == listOf(2.5) }
        assertEquals("", viewModel.uiState.value.plateDraft)
    }

    @Test
    fun rollingReorderLeavesNoGapsOrDuplicates() = runBlocking {
        val programId = rollingProgram()
        programRepo.setActive(programId)
        routineRepo.insertDay(RoutineDayEntity(programId = programId, dayOfWeek = null, sequenceIndex = 5, label = "c", isRest = false))
        routineRepo.insertDay(RoutineDayEntity(programId = programId, dayOfWeek = null, sequenceIndex = 9, label = "b", isRest = false))
        routineRepo.insertDay(RoutineDayEntity(programId = programId, dayOfWeek = null, sequenceIndex = 1, label = "a", isRest = false))
        val viewModel = settings(FakePreferences(), sample(false))
        awaitUntil { viewModel.uiState.value.days.size == 3 }

        viewModel.onEvent(SettingsEvent.MoveDay(from = 0, to = 2))

        awaitUntil {
            val days = routineRepo.days(programId)
            days.map { it.label } == listOf("c", "b", "a") && days.map { it.sequenceIndex } == listOf(0, 1, 2)
        }
        val days = routineRepo.days(programId)
        assertEquals(listOf(0, 1, 2), days.map { it.sequenceIndex })
        assertEquals(days.size, days.map { it.sequenceIndex }.distinct().size)
    }

    @Test
    fun clipTrimSettingsAreStoredInMilliseconds() = runBlocking {
        val viewModel = settings(FakePreferences(), sample(false))
        awaitUntil { viewModel.uiState.value.clipTailTrimSeconds == 3 }

        viewModel.onEvent(SettingsEvent.ClipTailTrim(5))
        viewModel.onEvent(SettingsEvent.ClipLeadTrim(2))

        awaitUntil { viewModel.uiState.value.clipTailTrimSeconds == 5 }
        awaitUntil { viewModel.uiState.value.clipLeadTrimSeconds == 2 }
    }

    @Test
    fun developerSectionFollowsTheSampleProvider() = runBlocking {
        val hidden = settings(FakePreferences(), sample(false))
        val shown = settings(FakePreferences(), sample(true))

        assertFalse(hidden.uiState.value.developerVisible)
        assertTrue(shown.uiState.value.developerVisible)
    }

    private suspend fun rollingProgram(): Long {
        val id = programRepo.insert(
            ProgramEntity(
                name = "rolling",
                scheduleMode = ScheduleMode.ROLLING,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        return id
    }

    private suspend fun fixedProgram(): Long {
        val id = programRepo.insert(
            ProgramEntity(
                name = "fixed",
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        programRepo.setActive(id)
        return id
    }

    private suspend fun insertDay(programId: Long, label: String, weekday: Int?, sequence: Int): Long =
        routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = label,
                dayOfWeek = weekday,
                sequenceIndex = sequence,
                isRest = false,
            ),
        )

    private fun settings(preferences: TrainingPreferencesRepository, sample: SampleProgramProvider): SettingsViewModel {
        val vm = SettingsViewModel(
            programs = programRepo,
            routines = routineRepo,
            preferences = preferences,
            sampleProgram = sample,
            backup = FakeBackup(),
            mediaPreferences = FakeMediaPreferences(),
        )
        activeViewModels.add(vm)
        return vm
    }

    private fun sample(available: Boolean) = object : SampleProgramProvider {
        override val available: Boolean = available

        override suspend fun program(): ProgramJson = error("not used")
    }

    private class FakePreferences : TrainingPreferencesRepository {
        private val reconciled = MutableStateFlow<LocalDate?>(null)
        val plateInventoryKgState = MutableStateFlow<List<Double>>(emptyList())
        val transitionRestState = MutableStateFlow(120)
        private val timerEnd = MutableStateFlow<Long?>(null)

        override val lastReconciledDate: Flow<LocalDate?> = reconciled
        override val plateInventoryKg: MutableStateFlow<List<Double>> = plateInventoryKgState
        override val transitionRestSeconds: MutableStateFlow<Int> = transitionRestState
        override val activeTimerEndElapsedRealtime: Flow<Long?> = timerEnd

        override suspend fun setLastReconciledDate(date: LocalDate?) {
            reconciled.value = date
        }

        override suspend fun setPlateInventoryKg(platesKg: List<Double>) {
            plateInventoryKgState.value = platesKg
        }

        override suspend fun setTransitionRestSeconds(seconds: Int) {
            transitionRestState.value = seconds
        }

        override suspend fun setActiveTimerEndElapsedRealtime(elapsedRealtime: Long?) {
            timerEnd.value = elapsedRealtime
        }

        val defaultRestState = MutableStateFlow(90)
        override val defaultRestSeconds: Flow<Int> = defaultRestState

        override suspend fun setDefaultRestSeconds(seconds: Int) {
            defaultRestState.value = seconds
        }
    }

    private class FakeMediaPreferences : MediaPreferencesRepository {
        override val leadTrimMs = MutableStateFlow(0L)
        override val tailTrimMs = MutableStateFlow(3_000L)

        override suspend fun setLeadTrimMs(ms: Long) {
            leadTrimMs.value = ms
        }

        override suspend fun setTailTrimMs(ms: Long) {
            tailTrimMs.value = ms
        }
    }

    private class FakeBackup : BackupClient {
        override val autoBackupEnabled = MutableStateFlow(false)
        override val autoBackupFolderUri = MutableStateFlow<String?>(null)
        override val lastAutoBackupDate = MutableStateFlow<LocalDate?>(null)
        override val progress = MutableStateFlow<BackupProgress?>(null)

        override suspend fun exportTo(uri: String) = Unit

        override suspend fun stageRestore(uri: String): BackupManifest = error(uri)

        override suspend fun commitRestore() = Unit

        override fun discardRestore() = Unit

        override suspend fun setAutoBackupEnabled(enabled: Boolean) = Unit

        override suspend fun setAutoBackupFolder(uri: String) = Unit
    }

    private class FakeProgramRepository : ProgramRepository {
        val programs = mutableMapOf<Long, ProgramEntity>()
        private val flow = MutableStateFlow(emptyList<ProgramEntity>())

        override fun observe(): Flow<ProgramEntity?> = flow.map { it.find { p -> p.isActive } }
        override fun observeAll(): Flow<List<ProgramEntity>> = flow
        override fun observeActive(): Flow<ProgramEntity?> = flow.map { it.find { p -> p.isActive } }
        override suspend fun get(): ProgramEntity? = programs.values.find { it.isActive }
        override suspend fun getById(id: Long): ProgramEntity? = programs[id]
        override suspend fun insert(program: ProgramEntity): Long {
            val id = (programs.keys.maxOrNull() ?: 0L) + 1
            programs[id] = program.copy(id = id)
            flow.value = programs.values.toList()
            return id
        }
        override suspend fun update(program: ProgramEntity) {
            programs[program.id] = program
            flow.value = programs.values.toList()
        }
        override suspend fun setActive(id: Long) {
            programs.forEach { (k, v) -> programs[k] = v.copy(isActive = k == id) }
            flow.value = programs.values.toList()
        }
        override suspend fun delete(id: Long) {
            programs.remove(id)
            flow.value = programs.values.toList()
        }
    }

    private class FakeRoutineRepository : RoutineRepository {
        val days = mutableMapOf<Long, RoutineDayEntity>()
        private val daysFlow = MutableStateFlow(emptyList<RoutineDayEntity>())

        override fun observeDays(programId: Long): Flow<List<RoutineDayEntity>> =
            daysFlow.map { it.filter { d -> d.programId == programId }.sortedBy { it.sequenceIndex } }

        override suspend fun getDay(id: Long): RoutineDayEntity? = days[id]
        override suspend fun insertDay(day: RoutineDayEntity): Long {
            val id = (days.keys.maxOrNull() ?: 0L) + 1
            days[id] = day.copy(id = id)
            daysFlow.value = days.values.toList()
            return id
        }
        override suspend fun updateDay(day: RoutineDayEntity) {
            days[day.id] = day
            daysFlow.value = days.values.toList()
        }
        override suspend fun deleteDay(id: Long) {
            days.remove(id)
            daysFlow.value = days.values.toList()
        }
        override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) {
            orderedDayIds.forEachIndexed { index, id ->
                days[id] = days[id]!!.copy(sequenceIndex = index)
            }
            daysFlow.value = days.values.toList()
        }
        override suspend fun days(programId: Long): List<RoutineDayEntity> =
            days.values.filter { it.programId == programId }.sortedBy { it.sequenceIndex }

        override suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity> = emptyList()
        override fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertChecklist(item: ChecklistItemEntity): Long = 0L
        override suspend fun updateChecklist(item: ChecklistItemEntity) {}
        override suspend fun deleteChecklist(id: Long) {}
        override fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>> = MutableStateFlow(emptyList())
        override suspend fun getSlot(id: Long): RoutineSlotEntity? = null
        override suspend fun insertSlot(slot: RoutineSlotEntity): Long = 0L
        override suspend fun updateSlot(slot: RoutineSlotEntity) {}
        override suspend fun deleteSlot(id: Long) {}
        override suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) {}
        override fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long = 0L
        override suspend fun deleteAlternative(id: Long) {}
        override fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?> = MutableStateFlow(null)
        override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = 0L
    }
}
