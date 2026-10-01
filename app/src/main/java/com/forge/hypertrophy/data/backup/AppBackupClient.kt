package com.forge.hypertrophy.data.backup

import android.content.Context
import androidx.core.net.toUri
import com.forge.hypertrophy.data.db.SCHEMA_VERSION
import com.forge.hypertrophy.data.repository.BackupPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface BackupClient {
    val autoBackupEnabled: Flow<Boolean>

    val autoBackupFolderUri: Flow<String?>

    val lastAutoBackupDate: Flow<LocalDate?>

    /** Progress of the export or restore in flight, null when idle. */
    val progress: StateFlow<BackupProgress?>

    suspend fun exportTo(uri: String)

    suspend fun stageRestore(uri: String): BackupManifest

    suspend fun commitRestore()

    fun discardRestore()

    suspend fun setAutoBackupEnabled(enabled: Boolean)

    suspend fun setAutoBackupFolder(uri: String)
}

@Singleton
class AppBackupClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: BackupCoordinator,
    private val preferences: BackupPreferencesRepository,
    private val folders: BackupFolders,
) : BackupClient {
    override val autoBackupEnabled = preferences.enabled
    override val autoBackupFolderUri = preferences.treeUri
    override val lastAutoBackupDate = preferences.lastDate

    private val _progress = MutableStateFlow<BackupProgress?>(null)
    override val progress: StateFlow<BackupProgress?> = _progress.asStateFlow()

    private var staged: File? = null

    override suspend fun exportTo(uri: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt")
            ?: error("Could not open the backup for writing")
        try {
            stream.use { coordinator.export(it) { update -> _progress.value = update } }
        } finally {
            _progress.value = null
        }
    }

    override suspend fun stageRestore(uri: String): BackupManifest = withContext(Dispatchers.IO) {
        discardRestore()
        val directory = File(context.cacheDir, "backup-stage")
        directory.deleteRecursively()
        directory.mkdirs()
        try {
            val stream = context.contentResolver.openInputStream(uri.toUri())
                ?: error("Could not open the backup")
            stream.use { BackupArchive.extract(it, directory) { update -> _progress.value = update } }
            val manifest = BackupArchive.readManifest(directory)
            manifest.validate(SCHEMA_VERSION)
            staged = directory
            manifest
        } catch (error: Throwable) {
            directory.deleteRecursively()
            staged = null
            throw error
        } finally {
            _progress.value = null
        }
    }

    override suspend fun commitRestore() {
        val directory = staged ?: error("No backup is waiting to be restored")
        coordinator.restore(directory)
        staged = null
    }

    override fun discardRestore() {
        staged?.deleteRecursively()
        staged = null
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
    }

    override suspend fun setAutoBackupFolder(uri: String) {
        folders.persist(uri)
        preferences.setTreeUri(uri)
        preferences.setEnabled(true)
    }
}

class FolderBackupWriter @Inject constructor(
    private val folders: BackupFolders,
    private val coordinator: BackupCoordinator,
) : BackupWriter {
    override suspend fun write(treeUri: String, displayName: String) {
        folders.write(treeUri, displayName) { output -> coordinator.export(output) }
    }
}
