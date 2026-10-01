package com.forge.hypertrophy.data.backup

import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.db.SCHEMA_VERSION
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.transfer.ProgramExporter
import java.io.File
import java.io.OutputStream
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface AppVersionSource {
    fun versionName(): String
}

fun interface AppRestarter {
    fun restart()
}

fun interface SqliteUserVersion {
    fun read(file: File): Int
}

class PlatformSqliteUserVersion @Inject constructor() : SqliteUserVersion {
    override fun read(file: File): Int = readSqliteUserVersion(file)
}

@Singleton
class BackupCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val databaseFile: File,
    private val exporter: ProgramExporter,
    private val preferences: PreferenceSnapshotStore,
    private val migrator: DatabaseMigrator,
    private val clock: Clock,
    private val appVersion: AppVersionSource,
    private val restarter: AppRestarter,
    private val userVersion: SqliteUserVersion,
    private val media: MediaFiles,
) {
    suspend fun export(output: OutputStream, onProgress: (BackupProgress) -> Unit = {}) = withContext(Dispatchers.IO) {
        database.checkpointWal()
        val programs = database.programDao().all().map { program -> exporter.export(program.id) }
        val files = media.list()
        BackupArchive.write(
            output = output,
            manifest = BackupManifest(
                schemaVersion = SCHEMA_VERSION,
                appVersion = appVersion.versionName(),
                exportedAt = clock.instant().toString(),
                mediaFiles = files.size,
            ),
            database = databaseFile,
            programs = programs,
            preferences = preferences.capture(),
            media = files,
            onProgress = onProgress,
        )
    }

    suspend fun restore(directory: File, restart: Boolean = true) = withContext(Dispatchers.IO) {
        val manifest = BackupArchive.readManifest(directory)
        manifest.validate(SCHEMA_VERSION)
        val incoming = File(directory, BackupArchive.DATABASE)
        if (!incoming.isFile) throw InvalidBackupException("Backup is missing the database")
        val fileVersion = userVersion.read(incoming)
        if (fileVersion > SCHEMA_VERSION) throw NewerBackupException(fileVersion, SCHEMA_VERSION)
        if (fileVersion < SCHEMA_VERSION) migrator.migrate(incoming, fileVersion)
        val snapshot = BackupArchive.readPreferences(directory)
        val mediaSwap = MediaSwap(media.directory)
        mediaSwap.stage(BackupArchive.extractedMedia(directory))
        val swap = DatabaseSwap(databaseFile)
        var closed = false
        try {
            swap.install(incoming) {
                database.close()
                closed = true
            }
            mediaSwap.install()
            preferences.restore(snapshot)
            swap.discardAside()
            mediaSwap.discardAside()
        } catch (error: Throwable) {
            if (closed) {
                swap.rollback()
                mediaSwap.rollback()
                if (restart) restarter.restart()
            } else {
                mediaSwap.rollback()
            }
            throw error
        }
        directory.deleteRecursively()
        if (restart) restarter.restart()
    }
}
