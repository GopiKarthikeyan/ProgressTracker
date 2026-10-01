package com.forge.hypertrophy.ui.screens.settings

import com.forge.hypertrophy.data.backup.BackupClient
import com.forge.hypertrophy.data.backup.BackupManifest
import com.forge.hypertrophy.data.backup.BackupProgress
import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.repository.MediaPreferencesRepository
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest : ViewModelDaoTest() {
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
        assertEquals(1, db.routineDao().getDay(first)!!.dayOfWeek)
        assertEquals(2, db.routineDao().getDay(second)!!.dayOfWeek)
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

        awaitUntil { db.routineDao().getDay(second)!!.dayOfWeek == 4 }
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
        val fixture = DaoFixture(db)
        val programId = fixture.program("rolling")
        db.programDao().setActive(programId)
        fixture.day(programId, sequenceIndex = 5, label = "c")
        fixture.day(programId, sequenceIndex = 9, label = "b")
        fixture.day(programId, sequenceIndex = 1, label = "a")
        val viewModel = settings(FakePreferences(), sample(false))
        awaitUntil { viewModel.uiState.value.days.size == 3 }

        viewModel.onEvent(SettingsEvent.MoveDay(from = 0, to = 2))

        awaitUntil {
            val days = db.routineDao().days(programId)
            days.map { it.label } == listOf("c", "b", "a") && days.map { it.sequenceIndex } == listOf(0, 1, 2)
        }
        val days = db.routineDao().days(programId)
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

    private suspend fun fixedProgram(): Long {
        val id = DaoFixture(db).program("fixed")
        val program = db.programDao().getById(id)!!
        db.programDao().update(program.copy(scheduleMode = ScheduleMode.FIXED))
        db.programDao().setActive(id)
        return id
    }

    private suspend fun insertDay(programId: Long, label: String, weekday: Int?, sequence: Int): Long =
        db.routineDao().insertDay(
            RoutineDayEntity(
                programId = programId,
                label = label,
                dayOfWeek = weekday,
                sequenceIndex = sequence,
                isRest = false,
            ),
        )

    private fun settings(preferences: TrainingPreferencesRepository, sample: SampleProgramProvider) = track(
        SettingsViewModel(
            programs = RoomProgramRepository(db.programDao()),
            routines = RoomRoutineRepository(db.routineDao()),
            preferences = preferences,
            sampleProgram = sample,
            backup = FakeBackup(),
            mediaPreferences = FakeMediaPreferences(),
        ),
    )

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
}
