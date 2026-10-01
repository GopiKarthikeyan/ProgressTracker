package com.forge.hypertrophy.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.db.DatabaseMigrations
import com.forge.hypertrophy.data.db.SCHEMA_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

fun interface DatabaseMigrator {
    fun migrate(databaseFile: File, fromSchemaVersion: Int)
}

fun readSqliteUserVersion(file: File): Int =
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }

class RoomDatabaseMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
) : DatabaseMigrator {
    override fun migrate(databaseFile: File, fromSchemaVersion: Int) {
        if (fromSchemaVersion > SCHEMA_VERSION) {
            throw NewerBackupException(fromSchemaVersion, SCHEMA_VERSION)
        }
        if (fromSchemaVersion == SCHEMA_VERSION) return
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.absolutePath)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
        try {
            database.checkpointWal()
        } finally {
            database.close()
        }
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }
}
