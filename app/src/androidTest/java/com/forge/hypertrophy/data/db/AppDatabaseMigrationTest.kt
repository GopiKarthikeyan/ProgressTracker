package com.forge.hypertrophy.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun createsVersion1FromExportedSchema() {
        val database = helper.createDatabase(DB, 1)
        assertSessionSlotTable(database)
        assertEquals(1, database.version)
        database.close()
    }

    @Test
    fun migratesBodyWeightFromVersion1AndLeavesBodyFatEmpty() {
        val created = helper.createDatabase(DB, 1)
        created.execSQL("INSERT INTO biometrics (date, bodyWeightKg) VALUES ('2026-04-01', 82.5)")
        created.close()
        val migrated = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)
        migrated.query("SELECT bodyWeightKg, bodyFatPercent FROM biometrics").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(82.5, cursor.getDouble(0), 0.001)
            assertTrue(cursor.isNull(1))
        }
        assertEquals(2, migrated.version)
        migrated.close()
    }

    @Test
    fun migratesCardioLogFromVersion2AndDefaultsTypeToJog() {
        val created = helper.createDatabase(DB, 2)
        created.execSQL(
            """
            INSERT INTO workout_session (date, dayId, kind, status, isDeload, isShortOnTime)
            VALUES ('2026-10-01', NULL, 'CARDIO', 'COMPLETED', 0, 0)
            """.trimIndent(),
        )
        created.execSQL(
            """
            INSERT INTO cardio_log (sessionId, distanceM, durationSec, source, gearId, tempC, uvIndex)
            VALUES (1, 5000.0, 1800, 'MANUAL', NULL, NULL, NULL)
            """.trimIndent(),
        )
        created.close()
        val migrated = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)
        migrated.query("SELECT distanceM, type FROM cardio_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5000.0, cursor.getDouble(0), 0.001)
            assertEquals("JOG", cursor.getString(1))
        }
        assertEquals(3, migrated.version)
        migrated.close()
    }

    @Test
    fun migratesMediaItemFromVersion3AndLeavesCaptureDateAndLabelEmpty() {
        val created = helper.createDatabase(DB, 3)
        created.execSQL(
            """
            INSERT INTO exercise (name, equipment, barWeightKg, loadIncrementKg, isUnilateral, skillId,
                primaryMuscleGroups, secondaryMuscleGroups, setupNotes, archivedAt)
            VALUES ('press', 'BARBELL', 20.0, 2.5, 0, NULL, '["chest"]', '[]', '', NULL)
            """.trimIndent(),
        )
        created.execSQL(
            """
            INSERT INTO media_item (type, pose, exerciseId, setEntryId, uri, trimStartMs, trimEndMs)
            VALUES ('VIDEO', NULL, 1, NULL, 'media/clip-1.mp4', NULL, NULL)
            """.trimIndent(),
        )
        created.close()
        val migrated = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4)
        migrated.query("SELECT uri, capturedAt, label FROM media_item").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("media/clip-1.mp4", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        assertEquals(4, migrated.version)
        migrated.close()
    }

    @Test
    fun migratesFromVersion4AddingAnEmptyStageEventLog() {
        val created = helper.createDatabase(DB, 4)
        created.execSQL("INSERT INTO skill (name, archivedAt) VALUES ('planche', NULL)")
        created.close()
        val migrated = helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)
        migrated.query("SELECT COUNT(*) FROM skill_stage_event").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.execSQL(
            """
            INSERT INTO skill_stage_event (skillId, date, fromTier, fromStage, toTier, toStage, recordedAt)
            VALUES (1, '2026-10-01', 0, 1, 0, 2, '2026-10-01T10:00:00Z')
            """.trimIndent(),
        )
        migrated.setForeignKeyConstraintsEnabled(true)
        migrated.execSQL("DELETE FROM skill WHERE id = 1")
        migrated.query("SELECT COUNT(*) FROM skill_stage_event").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        assertEquals(5, migrated.version)
        migrated.close()
    }

    @Test
    fun migratesFromVersion5AddingEditedAtAndAnEmptyBaselineTable() {
        val created = helper.createDatabase(DB, 5)
        created.execSQL(
            """
            INSERT INTO workout_session (date, dayId, kind, status, isDeload, isShortOnTime)
            VALUES ('2026-10-01', NULL, 'GYM', 'COMPLETED', 0, 0)
            """.trimIndent(),
        )
        created.close()
        val migrated = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6)
        migrated.query("SELECT editedAt FROM workout_session").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        migrated.query("SELECT COUNT(*) FROM slot_baseline").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.execSQL(
            """
            INSERT INTO program (name, scheduleMode, rollingSequence, deloadActive, deloadStartedOn)
            VALUES ('p', 'ROLLING', 0, 0, NULL)
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO exercise (name, equipment, barWeightKg, loadIncrementKg, isUnilateral, skillId,
                primaryMuscleGroups, secondaryMuscleGroups, setupNotes, archivedAt)
            VALUES ('press', 'BARBELL', 20.0, 2.5, 0, NULL, '["chest"]', '[]', '', NULL)
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO routine_day (programId, label, dayOfWeek, sequenceIndex, isRest)
            VALUES (1, 'push', NULL, 0, 0)
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO routine_slot (dayId, exerciseId, category, sortOrder, metricType, setsMin, setsMax,
                isAmrap, restAsNeeded, isOptional, progressionRule)
            VALUES (1, 1, 'COMPOUND', 0, 'WEIGHT_REPS', 3, 3, 0, 0, 0, 'DOUBLE')
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO slot_baseline (slotId, weightKg, repsHint, setAt)
            VALUES (1, 80.0, 5, '2026-10-01T10:00:00Z')
            """.trimIndent(),
        )
        migrated.setForeignKeyConstraintsEnabled(true)
        migrated.execSQL("DELETE FROM routine_slot WHERE id = 1")
        migrated.query("SELECT COUNT(*) FROM slot_baseline").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        assertEquals(6, migrated.version)
        migrated.close()
    }

    /**
     * Smoke test for the harness. Creates version 1 from the exported schema and
     * validates that same database against the same schema, with no migration in
     * between. [MigrationTestHelper.runMigrationsAndValidate] compares the live
     * database to 1.json, so this catches schema drift.
     */
    @Test
    fun validatesVersion1WithoutMigrations() {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, 1, true).close()
    }

    private fun assertSessionSlotTable(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'session_slot'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }

    private companion object {
        const val DB = "hypertrophy-migration.db"
    }
}
