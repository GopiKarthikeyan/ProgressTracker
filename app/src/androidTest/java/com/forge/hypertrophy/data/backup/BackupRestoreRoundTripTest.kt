package com.forge.hypertrophy.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forge.hypertrophy.data.db.AppDatabase
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

@RunWith(AndroidJUnit4::class)
class BackupRestoreRoundTripTest {
    @Test
    fun exportAndRestoreRoundTripsRowData() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "backup-instrumented-${System.nanoTime()}").apply { mkdirs() }
            val databaseFile = File(directory, "live.db")
            val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.absolutePath)
                .allowMainThreadQueries()
                .build()
            val preferences = MemoryPreferences()
            var restarted = false
            val coordinator = BackupCoordinator(
                database = database,
                databaseFile = databaseFile,
                exporter = ProgramExporter(database, ProgramExportPreferences.Builtin),
                preferences = preferences,
                migrator = RoomDatabaseMigrator(context),
                clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC),
                appVersion = AppVersionSource { "0.1.0" },
                restarter = AppRestarter { restarted = true },
                userVersion = PlatformSqliteUserVersion(),
                media = MediaFiles(File(directory, "files")),
            )
            val programId = database.programDao().insert(
                ProgramEntity(
                    name = "Hypertrophy",
                    scheduleMode = ScheduleMode.ROLLING,
                    rollingSequence = 0,
                    deloadActive = false,
                    deloadStartedOn = null,
                ),
            )
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
            val slotId = database.routineDao().insertSlot(
                RoutineSlotEntity(
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
                ),
            )
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
            val sessionSlotId = database.sessionDao().insertSlot(
                SessionSlotEntity(
                    sessionId = sessionId,
                    slotId = slotId,
                    prescriptionSnapshot = SlotPrescription(
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
                    ),
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

            val zip = File(directory, "backup.zip")
            zip.outputStream().use { coordinator.export(it) }
            val extracted = File(directory, "extracted")
            zip.inputStream().use { BackupArchive.extract(it, extracted) }
            SQLiteDatabase.openDatabase(File(extracted, BackupArchive.DATABASE).path, null, SQLiteDatabase.OPEN_READONLY)
                .use { raw ->
                    raw.rawQuery("SELECT reps FROM set_entry", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(8, cursor.getInt(0))
                    }
                }

            val program = database.programDao().getById(programId)!!
            database.programDao().update(program.copy(name = "changed"))
            val set = database.sessionDao().observeSets(sessionSlotId).first().single()
            database.sessionDao().updateSet(set.copy(reps = 1, weightKg = 1.0))
            database.biometricsDao().upsert(BiometricsEntity(date = LocalDate.of(2026, 4, 1), bodyWeightKg = 70.0))
            preferences.current = PreferenceSnapshot(files = mapOf("training" to emptyList(), "onboarding" to emptyList()))

            coordinator.restore(extracted, restart = true)
            assertTrue(restarted)
            val restored = Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.absolutePath)
                .allowMainThreadQueries()
                .build()
            assertEquals("Hypertrophy", restored.programDao().getById(programId)!!.name)
            assertEquals("weighted pull-ups", restored.exerciseDao().get(exerciseId)!!.name)
            assertEquals(82.5, restored.biometricsDao().get(LocalDate.of(2026, 4, 1))!!.bodyWeightKg)
            val restoredSet = restored.sessionDao().observeSets(sessionSlotId).first().single()
            assertEquals(8, restoredSet.reps)
            assertEquals(60.0, restoredSet.weightKg)
            assertEquals("[20.0,10.0]", preferences.current.files.getValue("training").single().value)
            restored.close()
            directory.deleteRecursively()
        }
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
}
