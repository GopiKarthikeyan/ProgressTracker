package com.forge.hypertrophy.data.db

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class AppDatabaseMigrationTest {
    @Test
    fun createsVersion1FromExportedSchema() {
        val database = createDatabase(CREATE_DB)
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'session_slot'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        assertEquals(1, database.version)
        database.close()
    }

    /**
     * Smoke test for the harness. Creates version 1 from the exported schema and
     * validates that same database against the same schema, with no migration in
     * between. It proves the schema file is found and parseable. It is not
     * migration coverage. That starts at version 2.
     */
    @Test
    fun validatesVersion1WithoutMigrations() {
        createDatabase(VALIDATE_DB).close()
        val schema = loadExportedSchema()
        val reopened = openExisting(VALIDATE_DB, schema.version)
        reopened.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'session_slot'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        assertEquals(schema.version, reopened.version)
        reopened.close()
    }

    private fun createDatabase(name: String): SupportSQLiteDatabase {
        val schema = loadExportedSchema()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(schema.version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        schema.statements.forEach(db::execSQL)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        ).writableDatabase
    }

    private fun openExisting(name: String, version: Int): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        error("Reopen created a new database; version 1 was not preserved")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        error("A migration ran between version $oldVersion and $newVersion")
                    }
                })
                .build(),
        ).writableDatabase
    }

    private fun loadExportedSchema(): ExportedSchema {
        val resourceName = "com.forge.hypertrophy.data.db.AppDatabase/1.json"
        val text = checkNotNull(javaClass.classLoader).getResourceAsStream(resourceName).use { stream ->
            checkNotNull(stream) { "Exported schema $resourceName is not on the test classpath" }
                .bufferedReader()
                .readText()
        }
        val database = JSONObject(text).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val statements = mutableListOf<String>()
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            statements += entity.getString("createSql").replace(TABLE_PLACEHOLDER, tableName)
            val indices = entity.optJSONArray("indices") ?: continue
            for (indexIndex in 0 until indices.length()) {
                statements += indices.getJSONObject(indexIndex)
                    .getString("createSql")
                    .replace(TABLE_PLACEHOLDER, tableName)
            }
        }
        val setupQueries = database.optJSONArray("setupQueries")
        if (setupQueries != null) {
            for (index in 0 until setupQueries.length()) {
                statements += setupQueries.getString(index)
            }
        }
        return ExportedSchema(database.getInt("version"), statements)
    }

    private data class ExportedSchema(
        val version: Int,
        val statements: List<String>,
    )

    private companion object {
        const val CREATE_DB = "hypertrophy-create.db"
        const val VALIDATE_DB = "hypertrophy-validate.db"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
