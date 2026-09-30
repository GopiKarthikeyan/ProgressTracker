package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.domain.model.ScheduleMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramImporterTest : DaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun midWriteFailureRollsBackAndSkipsPreferences() = runBlocking {
        val preferences = RecordingPreferences()
        val importer = ProgramImporter(
            database = db,
            clock = clock,
            preferences = preferences,
            beforeCommit = { error("boom") },
        )

        val failure = runCatching {
            importer.import(programDocument(), ImportMode.ADD_PROGRAM)
        }

        assertTrue(failure.isFailure)
        assertTrue(db.programDao().all().isEmpty())
        assertTrue(db.exerciseDao().all().isEmpty())
        assertTrue(db.skillDao().all().isEmpty())
        assertTrue(db.routineDao().allSlots().isEmpty())
        assertEquals(0, preferences.calls)
    }

    @Test
    fun invalidDocumentWritesNothing() = runBlocking {
        val preferences = RecordingPreferences()
        val importer = ProgramImporter(db, clock, preferences)

        val result = importer.import(programDocument(schemaVersion = 2), ImportMode.ADD_PROGRAM)

        assertTrue(result is ImportResult.Rejected)
        assertTrue(db.programDao().all().isEmpty())
        assertEquals(0, preferences.calls)
    }

    @Test
    fun replaceRoutineMergesLibraryByName() = runBlocking {
        val fixture = DaoFixture(db)
        val pressId = fixture.exercise("Press")
        val oldId = fixture.exercise("Old Lift")
        val loggedId = fixture.exercise("Logged Lift")
        val activeId = fixture.program("current")
        db.programDao().setActive(activeId)
        fixture.slot(fixture.day(activeId, label = "old day"), pressId)
        val otherId = fixture.program("other")
        fixture.slot(fixture.day(otherId, label = "kept day"), loggedId)
        val preferences = RecordingPreferences()
        val importer = ProgramImporter(db, clock, preferences)
        val incoming = programDocument(
            name = "Imported",
            exercises = listOf(
                programExercise("press", name = " press "),
                programExercise("row", name = "Row"),
            ),
            days = listOf(programDay(slots = listOf(programSlot(exerciseKey = "press")))),
        )

        val result = importer.import(incoming, ImportMode.REPLACE_ROUTINE_MERGE_LIBRARY)

        assertTrue(result is ImportResult.Imported)
        assertEquals(pressId, db.exerciseDao().get(pressId)!!.id)
        assertEquals("press", db.exerciseDao().get(pressId)!!.name)
        assertEquals(oldId, db.exerciseDao().get(oldId)!!.id)
        assertEquals(loggedId, db.exerciseDao().get(loggedId)!!.id)
        assertEquals("Row", db.exerciseDao().all().single { it.name == "Row" }.name)
        assertEquals(listOf("mon"), db.routineDao().days(activeId).map { it.label })
        assertEquals(listOf("kept day"), db.routineDao().days(otherId).map { it.label })
        assertEquals(1, preferences.calls)
        assertEquals(120, preferences.rest)
        assertEquals(listOf(20.0, 10.0), preferences.plates)
    }

    @Test
    fun replaceEverythingDeletesUnreferencedRowsAndArchivesReferencedOnes() = runBlocking {
        val fixture = DaoFixture(db)
        val pressId = fixture.exercise("Press")
        fixture.exercise("Old Lift")
        val loggedId = fixture.exercise("Logged Lift")
        val activeId = fixture.program("current")
        db.programDao().setActive(activeId)
        fixture.slot(fixture.day(activeId, label = "old day"), pressId)
        val otherId = fixture.program("other")
        fixture.slot(fixture.day(otherId, label = "kept day"), loggedId)
        val importer = ProgramImporter(db, clock, RecordingPreferences())
        val incoming = programDocument(
            name = "Imported",
            exercises = listOf(programExercise("press", name = "Press")),
            days = listOf(programDay(slots = listOf(programSlot(exerciseKey = "press")))),
        )

        val result = importer.import(incoming, ImportMode.REPLACE_EVERYTHING) as ImportResult.Imported

        assertEquals(listOf("Logged Lift"), result.preview.exercisesToArchive)
        assertNull(db.exerciseDao().all().singleOrNull { it.name == "Old Lift" })
        val logged = db.exerciseDao().get(loggedId)!!
        assertEquals(clock.instant(), logged.archivedAt)
        assertEquals(pressId, db.exerciseDao().all().single { it.name == "Press" }.id)
        assertEquals(listOf("kept day"), db.routineDao().days(otherId).map { it.label })
    }

    @Test
    fun addProgramKeepsTheCurrentRoutineAndStaysInactive() = runBlocking {
        val fixture = DaoFixture(db)
        val existingId = fixture.program("kept")
        db.programDao().setActive(existingId)
        fixture.day(existingId, label = "kept day")
        val importer = ProgramImporter(db, clock, ProgramImportPreferences.None)

        val result = importer.import(
            programDocument(name = "Added"),
            ImportMode.ADD_PROGRAM,
            applyDefaults = false,
        ) as ImportResult.Imported

        val programs = db.programDao().all()
        assertEquals(2, programs.size)
        assertEquals(existingId, first(db.programDao().observeActive())!!.id)
        assertEquals(false, programs.single { it.id == result.programId }.isActive)
        assertEquals("Added", programs.single { it.id == result.programId }.name)
        assertEquals(listOf("kept day"), db.routineDao().days(existingId).map { it.label })
        assertEquals(1, db.routineDao().days(result.programId).size)
    }

    @Test
    fun addProgramBecomesActiveWhenNoneExists() = runBlocking {
        val importer = ProgramImporter(db, clock, ProgramImportPreferences.None)

        val result = importer.import(programDocument(), ImportMode.ADD_PROGRAM, applyDefaults = false)
            as ImportResult.Imported

        assertEquals(true, db.programDao().getById(result.programId)!!.isActive)
        assertEquals(ScheduleMode.FIXED, db.programDao().getById(result.programId)!!.scheduleMode)
    }

    @Test
    fun sampleDocumentImportsTheExpectedCounts() = runBlocking {
        val parsed = ProgramJsonFormat.decodeFromString<ProgramJson>(repoFile("docs/program.json").readText())
        val report = ProgramJsonValidator.validate(parsed, ScheduleMode.FIXED)
        assertEquals(emptyList<ProgramJsonIssue>(), report.errors)
        assertEquals(3, parsed.skills.size)
        assertEquals(50, parsed.exercises.size)
        assertEquals(7, parsed.days.size)
        assertEquals(57, parsed.days.sumOf { it.slots.size })
        assertEquals(1, parsed.days.sumOf { day -> day.slots.sumOf { it.alternativeExerciseKeys.size } })
        assertEquals(3, parsed.days.sumOf { it.cardio.size })
        assertEquals(
            35,
            parsed.days.sumOf { day ->
                (day.prep?.items?.size ?: 0) + (day.cooldown?.items?.size ?: 0)
            },
        )
        val importer = ProgramImporter(db, clock, ProgramImportPreferences.None)

        val result = importer.import(parsed, ImportMode.ADD_PROGRAM, applyDefaults = false)
            as ImportResult.Imported

        val days = db.routineDao().days(result.programId)
        val dayIds = days.map { it.id }
        val slots = db.routineDao().slotsForDays(dayIds)
        assertEquals(7, days.size)
        assertEquals(57, slots.size)
        assertEquals(1, db.routineDao().alternativesForSlots(slots.map { it.id }).size)
        assertEquals(3, db.routineDao().cardioForDays(dayIds).size)
        assertEquals(35, db.routineDao().checklistForDays(dayIds).size)
        assertEquals(50, db.exerciseDao().all().size)
        assertEquals(3, db.skillDao().all().size)
        assertEquals(
            "Calisthenics & Hypertrophy — 6-Day Protocol",
            db.programDao().getById(result.programId)!!.name,
        )
    }
}

private class RecordingPreferences : ProgramImportPreferences {
    var calls: Int = 0
    var rest: Int? = null
    var plates: List<Double>? = null

    override suspend fun applyDefaults(transitionRestSeconds: Int, plateInventoryKg: List<Double>) {
        calls += 1
        rest = transitionRestSeconds
        plates = plateInventoryKg
    }
}
