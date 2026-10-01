package com.forge.hypertrophy.ui.screens.transfer

import androidx.lifecycle.SavedStateHandle
import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.storage.ProgramDocumentStore
import com.forge.hypertrophy.data.transfer.ImportMode
import com.forge.hypertrophy.data.transfer.ProgramImportPreferences
import com.forge.hypertrophy.data.transfer.ProgramImporter
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import com.forge.hypertrophy.data.transfer.programDocument
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPreviewViewModelTest : ViewModelDaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun previewIsPopulatedFromAValidDocument() = runBlocking {
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        val state = viewModel.uiState.value
        assertEquals("Protocol", state.programName)
        assertEquals(1, state.dayCount)
        assertEquals(1, state.slotCount)
        assertEquals(1, state.newExerciseCount)
        assertEquals(0, state.newSkillCount)
        assertTrue(state.errors.isEmpty())
        assertTrue(state.canConfirm)
        assertEquals(ImportMode.REPLACE_ROUTINE_MERGE_LIBRARY, state.mode)
        assertTrue(state.applyDefaults)
    }

    @Test
    fun aDocumentWithErrorsCannotBeConfirmed() = runBlocking {
        val preferences = RecordingPreferences()
        val viewModel = preview(programDocument(schemaVersion = 2), preferences)
        awaitUntil { !viewModel.uiState.value.loading }
        assertFalse(viewModel.uiState.value.canConfirm)
        assertTrue(viewModel.uiState.value.errors.isNotEmpty())

        viewModel.onEvent(ImportPreviewEvent.Confirm)

        assertFalse(viewModel.uiState.value.imported)
        assertTrue(db.programDao().all().isEmpty())
        assertEquals(0, preferences.calls)
    }

    @Test
    fun addProgramModeReachesTheImporter() = runBlocking {
        seedExistingLibrary()
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Mode(ImportMode.ADD_PROGRAM))
        awaitUntil { viewModel.uiState.value.mode == ImportMode.ADD_PROGRAM && !viewModel.uiState.value.loading }
        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(listOf("old", "Protocol"), db.programDao().all().map { it.name })
        assertNotNull(db.exerciseDao().all().firstOrNull { it.name == "unrelated" })
    }

    @Test
    fun replaceRoutineModeReachesTheImporter() = runBlocking {
        seedExistingLibrary()
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(listOf("Protocol"), db.programDao().all().map { it.name })
        val unrelated = db.exerciseDao().all().firstOrNull { it.name == "unrelated" }
        assertNotNull(unrelated)
        assertNull(unrelated!!.archivedAt)
    }

    @Test
    fun replaceEverythingModeReachesTheImporter() = runBlocking {
        seedExistingLibrary()
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Mode(ImportMode.REPLACE_EVERYTHING))
        awaitUntil { viewModel.uiState.value.mode == ImportMode.REPLACE_EVERYTHING }
        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(listOf("Protocol"), db.programDao().all().map { it.name })
        assertNull(db.exerciseDao().all().firstOrNull { it.name == "unrelated" })
    }

    @Test
    fun preferencesOptOutSuppressesTheDefaultsWrite() = runBlocking {
        val preferences = RecordingPreferences()
        val viewModel = preview(programDocument(), preferences)
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.ApplyDefaults(false))
        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(0, preferences.calls)
        assertEquals(1, db.programDao().all().size)
    }

    @Test
    fun preferencesOptInWritesTheDefaultsAfterImport() = runBlocking {
        val preferences = RecordingPreferences()
        val viewModel = preview(programDocument(), preferences)
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(1, preferences.calls)
        assertEquals(120, preferences.transitionRest)
        assertEquals(listOf(20.0, 10.0), preferences.plates)
    }

    private suspend fun seedExistingLibrary() {
        val fixture = DaoFixture(db)
        db.programDao().setActive(fixture.program("old"))
        fixture.exercise("unrelated")
    }

    private fun preview(document: ProgramJson, preferences: ProgramImportPreferences) = track(
        ImportPreviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("uri" to "content://picked/program.json")),
            documents = FakeDocumentStore(document),
            sampleProgram = UnavailableSample,
            importer = ProgramImporter(db, clock, preferences),
            programs = RoomProgramRepository(db.programDao()),
        ),
    )

    private class FakeDocumentStore(private val document: ProgramJson) : ProgramDocumentStore {
        override suspend fun read(uri: String): ProgramJson = document

        override suspend fun write(uri: String, document: ProgramJson) = error("not used")
    }

    private object UnavailableSample : SampleProgramProvider {
        override val available: Boolean = false

        override suspend fun program(): ProgramJson = error("unavailable")
    }

    private class RecordingPreferences : ProgramImportPreferences {
        var calls = 0
        var transitionRest: Int? = null
        var plates: List<Double>? = null

        override suspend fun applyDefaults(transitionRestSeconds: Int, plateInventoryKg: List<Double>) {
            calls += 1
            transitionRest = transitionRestSeconds
            plates = plateInventoryKg
        }
    }
}
