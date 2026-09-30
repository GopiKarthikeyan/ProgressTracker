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

    /**
     * Smoke test for the harness. Creates version 1 from the exported schema and
     * validates that same database against the same schema, with no migration in
     * between. [MigrationTestHelper.runMigrationsAndValidate] compares the live
     * database to 1.json, so this catches schema drift. It is not migration
     * coverage. That starts at version 2.
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
