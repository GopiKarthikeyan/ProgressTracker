package com.forge.hypertrophy.data.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.db.SCHEMA_VERSION
import com.forge.hypertrophy.data.entity.BiometricsEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.transfer.ProgramExportPreferences
import com.forge.hypertrophy.data.transfer.ProgramExporter
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class BackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun roundTripRestoresRowsSettingsAndCheckpointedDatabase() = runBlocking {
        val harness = Harness()
        val seeded = harness.seed()
        val zip = File(harness.directory, "backup.zip")
        zip.outputStream().use { harness.coordinator.export(it) }

        val extracted = File(harness.directory, "extracted")
        zip.inputStream().use { BackupArchive.extract(it, extracted) }
        SQLiteDatabase.openDatabase(File(extracted, BackupArchive.DATABASE).path, null, SQLiteDatabase.OPEN_READONLY)
            .use { raw ->
                raw.rawQuery("SELECT name FROM program", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Hypertrophy", cursor.getString(0))
                }
            }
        assertTrue(File(extracted, BackupArchive.PROGRAMS).readText().contains("Hypertrophy"))

        harness.mutate(seeded)
        harness.preferences.current = PreferenceSnapshot(files = mapOf("training" to emptyList(), "onboarding" to emptyList()))
        harness.coordinator.restore(extracted, restart = true)

        assertEquals(1, harness.restarts)
        val restored = harness.reopen()
        assertEquals("Hypertrophy", restored.programDao().getById(seeded.programId)!!.name)
        assertEquals("weighted pull-ups", restored.exerciseDao().get(seeded.exerciseId)!!.name)
        assertEquals(82.5, restored.biometricsDao().get(LocalDate.of(2026, 4, 1))!!.bodyWeightKg)
        val set = restored.sessionDao().observeSets(seeded.sessionSlotId).first().single()
        assertEquals(8, set.reps)
        assertEquals(60.0, set.weightKg)
        assertEquals("[20.0,10.0]", harness.preferences.current.files.getValue("training").single().value)
        restored.close()
    }

    @Test
    fun mediaFilesTravelWithTheBackupAndComeBackOnRestore() = runBlocking {
        val harness = Harness()
        harness.seed()
        harness.writeClip("clip-1.mp4", "first-clip")
        val progress = mutableListOf<BackupProgress>()
        val zip = File(harness.directory, "media.zip")
        zip.outputStream().use { harness.coordinator.export(it) { progress += it } }
        assertEquals(1f, progress.last().fraction)
        assertTrue(progress.first().bytesDone < progress.last().bytesDone)

        val extracted = File(harness.directory, "media-extracted")
        zip.inputStream().use { BackupArchive.extract(it, extracted) }
        assertEquals("first-clip", BackupArchive.extractedMedia(extracted).single().readText())
        assertEquals(1, BackupArchive.readManifest(extracted).mediaFiles)

        File(harness.media.directory, "clip-1.mp4").delete()
        harness.writeClip("clip-2.mp4", "later-clip")
        harness.coordinator.restore(extracted, restart = false)

        val names = harness.media.list().map { it.name }
        assertEquals(listOf("clip-1.mp4"), names)
        assertEquals("first-clip", harness.media.list().single().readText())
        harness.reopen().close()
    }

    @Test
    fun newerSchemaLeavesTheLiveDatabaseUntouched() = runBlocking {
        val harness = Harness()
        val seeded = harness.seed()
        val zip = File(harness.directory, "newer.zip")
        harness.database.checkpointWal()
        zip.outputStream().use { output ->
            BackupArchive.write(
                output,
                BackupManifest(SCHEMA_VERSION + 1, "9.0.0", "2026-10-01T00:00:00Z"),
                harness.databaseFile,
                emptyList(),
                harness.preferences.capture(),
            )
        }
        val extracted = File(harness.directory, "newer")
        zip.inputStream().use { BackupArchive.extract(it, extracted) }
        try {
            harness.coordinator.restore(extracted, restart = false)
            error("newer schema was restored")
        } catch (error: NewerBackupException) {
            assertEquals(SCHEMA_VERSION + 1, error.schemaVersion)
        }
        assertEquals("Hypertrophy", harness.database.programDao().getById(seeded.programId)!!.name)
        assertEquals(0, harness.restarts)
        harness.database.close()
    }

    @Test
    fun olderSchemaIsMigratedBeforeTheSwap() = runBlocking {
        val migrated = mutableListOf<Int>()
        val harness = Harness(userVersion = SqliteUserVersion { 0 }, migrator = DatabaseMigrator { _, from -> migrated += from })
        harness.seed()
        val zip = File(harness.directory, "older.zip")
        zip.outputStream().use { harness.coordinator.export(it) }
        val extracted = File(harness.directory, "older")
        zip.inputStream().use { BackupArchive.extract(it, extracted) }
        harness.coordinator.restore(extracted, restart = false)
        assertEquals(listOf(0), migrated)
        val restored = harness.reopen()
        assertEquals("Hypertrophy", restored.programDao().get()!!.name)
        restored.close()
    }

    @Test
    fun currentSchemaIsNotRebuilt() {
        val file = File(context.cacheDir, "backup-current-${System.nanoTime()}.db")
        val database = open(file)
        val id = runBlocking {
            database.programDao().insert(
                ProgramEntity(
                    name = "kept",
                    scheduleMode = ScheduleMode.ROLLING,
                    rollingSequence = 0,
                    deloadActive = false,
                    deloadStartedOn = null,
                ),
            )
        }
        database.close()
        RoomDatabaseMigrator(context).migrate(file, SCHEMA_VERSION)
        val reopened = open(file)
        assertEquals("kept", runBlocking { reopened.programDao().getById(id)!!.name })
        assertEquals(SCHEMA_VERSION, reopened.openHelper.writableDatabase.version)
        reopened.close()
    }

    private fun open(file: File): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
        .allowMainThreadQueries()
        .build()

    private inner class Harness(
        userVersion: SqliteUserVersion = PlatformSqliteUserVersion(),
        migrator: DatabaseMigrator = RoomDatabaseMigrator(context),
    ) {
        val directory = File(context.cacheDir, "backup-roundtrip-${System.nanoTime()}").apply { mkdirs() }
        val databaseFile = File(directory, "live.db")
        val database = open(databaseFile)
        val preferences = MemoryPreferences()
        val media = MediaFiles(File(directory, "files"))
        var restarts = 0
        val coordinator = BackupCoordinator(
            database = database,
            databaseFile = databaseFile,
            exporter = ProgramExporter(database, ProgramExportPreferences.Builtin),
            preferences = preferences,
            migrator = migrator,
            clock = clock,
            appVersion = AppVersionSource { "0.1.0" },
            restarter = AppRestarter { restarts += 1 },
            userVersion = userVersion,
            media = media,
        )

        fun writeClip(name: String, bytes: String): File {
            media.directory.mkdirs()
            return File(media.directory, name).apply { writeText(bytes) }
        }

        suspend fun seed(): Seed {
            val programId = database.programDao().insert(
                ProgramEntity(
                    name = "Hypertrophy",
                    scheduleMode = ScheduleMode.ROLLING,
                    rollingSequence = 0,
                    deloadActive = false,
                    deloadStartedOn = null,
                ),
            )
            database.programDao().setActive(programId)
            val exerciseId = database.exerciseDao().insert(
                ExerciseEntity(
                    name = "weighted pull-ups",
                    equipment = Equipment.BARBELL,
                    barWeightKg = 20.0,
                    loadIncrementKg = 2.5,
                    isUnilateral = false,
                    skillId = null,
                    primaryMuscleGroups = listOf("back"),
                    secondaryMuscleGroups = emptyList(),
                    setupNotes = "squeeze",
                    archivedAt = null,
                ),
            )
            val dayId = database.routineDao().insertDay(
                RoutineDayEntity(programId = programId, label = "pull", dayOfWeek = null, sequenceIndex = 0, isRest = false),
            )
            val slotId = database.routineDao().insertSlot(routineSlot(dayId, exerciseId))
            val sessionId = database.sessionDao().insert(
                WorkoutSessionEntity(
                    date = LocalDate.of(2026, 4, 1),
                    dayId = dayId,
                    kind = SessionKind.GYM,
                    status = SessionStatus.COMPLETED,
                    isDeload = false,
                    isShortOnTime = false,
                    readinessSleep = null,
                    readinessSoreness = null,
                    readinessEnergy = null,
                    startedAt = null,
                    completedAt = null,
                ),
            )
            val prescription = prescription(exerciseId)
            val sessionSlotId = database.sessionDao().insertSlot(
                SessionSlotEntity(
                    sessionId = sessionId,
                    slotId = slotId,
                    prescriptionSnapshot = prescription,
                    chosenAlternativeExerciseId = null,
                    skipped = false,
                    skipReason = null,
                    formConfirmed = null,
                ),
            )
            database.sessionDao().insertSet(
                SetEntryEntity(
                    sessionSlotId = sessionSlotId,
                    setNumber = 1,
                    side = SetSide.BOTH,
                    setType = SetType.WORKING,
                    weightKg = 60.0,
                    reps = 8,
                    holdSec = null,
                    rpe = null,
                    jointFlags = emptyList(),
                    entryMethod = EntryMethod.SCREEN,
                    loggedAt = Instant.parse("2026-04-01T08:00:00Z"),
                ),
            )
            database.biometricsDao().upsert(BiometricsEntity(date = LocalDate.of(2026, 4, 1), bodyWeightKg = 82.5))
            return Seed(programId, exerciseId, sessionSlotId)
        }

        suspend fun mutate(seed: Seed) {
            val program = database.programDao().getById(seed.programId)!!
            database.programDao().update(program.copy(name = "changed"))
            val exercise = database.exerciseDao().get(seed.exerciseId)!!
            database.exerciseDao().update(exercise.copy(name = "changed"))
            val set = database.sessionDao().observeSets(seed.sessionSlotId).first().single()
            database.sessionDao().updateSet(set.copy(reps = 1, weightKg = 1.0))
            database.biometricsDao().upsert(BiometricsEntity(date = LocalDate.of(2026, 4, 1), bodyWeightKg = 70.0))
        }

        fun reopen(): AppDatabase = open(databaseFile)
    }

    private class MemoryPreferences : PreferenceSnapshotStore {
        var current = PreferenceSnapshot(
            files = mapOf(
                "training" to listOf(StoredPreference("plate_inventory_kg", "string", "[20.0,10.0]")),
                "onboarding" to listOf(StoredPreference("onboarding_complete", "boolean", "true")),
            ),
        )

        override suspend fun capture(): PreferenceSnapshot = current

        override suspend fun restore(snapshot: PreferenceSnapshot) {
            current = snapshot
        }
    }

    private data class Seed(val programId: Long, val exerciseId: Long, val sessionSlotId: Long)
}

private fun routineSlot(dayId: Long, exerciseId: Long) = RoutineSlotEntity(
    dayId = dayId,
    exerciseId = exerciseId,
    category = SlotCategory.COMPOUND,
    sortOrder = 0,
    supersetGroup = null,
    metricType = MetricType.WEIGHT_REPS,
    setsMin = 3,
    setsMax = 3,
    repsLow = 5,
    repsHigh = 8,
    isAmrap = false,
    holdTargetSec = null,
    blockDurationSec = null,
    restMinSec = 60,
    restMaxSec = 90,
    restAsNeeded = false,
    isOptional = false,
    skipReasonLabel = null,
    targetSkillStepId = null,
    progressionRule = ProgressionRule.DOUBLE,
    incrementOverrideKg = null,
)

private fun prescription(exerciseId: Long) = SlotPrescription(
    exerciseId = exerciseId,
    category = SlotCategory.COMPOUND,
    sortOrder = 0,
    metricType = MetricType.WEIGHT_REPS,
    setsMin = 3,
    setsMax = 3,
    repsLow = 5,
    repsHigh = 8,
    restMinSec = 60,
    restMaxSec = 90,
    progressionRule = ProgressionRule.DOUBLE,
)
