package com.forge.hypertrophy.data.backup

import android.content.Context
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.db.DATABASE_NAME
import com.forge.hypertrophy.data.db.SCHEMA_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

fun interface SnapshotInstaller {
    fun install(snapshot: File)
}

@Singleton
class RoomSnapshotInstaller @Inject constructor(
    private val database: AppDatabase,
    private val databaseFile: File,
    private val restarter: AppRestarter,
) : SnapshotInstaller {
    override fun install(snapshot: File) {
        installSnapshot(databaseFile, snapshot) { database.close() }
        restarter.restart()
    }
}

@Singleton
class SnapshotLibrary @Inject constructor(
    @ApplicationContext context: Context,
    clock: Clock,
) {
    private val store = PreMigrationSnapshots(
        databaseFile = context.getDatabasePath(DATABASE_NAME),
        snapshotsDir = File(context.filesDir, SNAPSHOT_DIR),
        currentVersion = SCHEMA_VERSION,
        clock = clock,
    )

    fun list(): List<SchemaSnapshot> = store.list()
}
