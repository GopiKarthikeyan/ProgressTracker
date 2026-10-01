package com.forge.hypertrophy.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Clock

const val SNAPSHOT_DIR = "db-snapshots"
private const val KEEP_SNAPSHOTS = 5

data class SchemaSnapshot(
    val file: File,
    val schemaVersion: Int,
    val label: String,
)

/**
 * Copies the live database before Room migrates it. The version is read with
 * a read-only connection. A file that is already at [currentVersion], or that
 * does not exist yet, is left alone.
 */
class PreMigrationSnapshots(
    private val databaseFile: File,
    private val snapshotsDir: File,
    private val currentVersion: Int,
    private val clock: Clock,
    private val readVersion: (File) -> Int = ::readSqliteUserVersion,
    private val keep: Int = KEEP_SNAPSHOTS,
) {
    fun captureIfStale(): File? {
        if (!databaseFile.isFile) return null
        val version = runCatching { readVersion(databaseFile) }.getOrNull() ?: return null
        if (version <= 0 || version >= currentVersion) return null
        checkpoint(databaseFile)
        snapshotsDir.mkdirs()
        val stamp = clock.instant().toString().replace(':', '-')
        val dest = File(snapshotsDir, "hypertrophy-v$version-$stamp.db")
        if (!dest.exists()) databaseFile.copyTo(dest)
        prune()
        return dest
    }

    fun list(): List<SchemaSnapshot> {
        val files = snapshotsDir.listFiles { file ->
            file.isFile && file.name.startsWith("hypertrophy-v") && file.name.endsWith(".db")
        }?.toList().orEmpty()
        return files.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            val version = versionIn(file.name) ?: return@mapNotNull null
            SchemaSnapshot(file, version, file.nameWithoutExtension.removePrefix("hypertrophy-v$version-"))
        }
    }

    private fun prune() {
        val extras = list().drop(keep)
        extras.forEach { snapshot -> snapshot.file.delete() }
    }

    private fun checkpoint(file: File) {
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor -> cursor.moveToFirst() }
            }
        }
    }

    private fun versionIn(name: String): Int? {
        val match = Regex("hypertrophy-v(\\d+)-").find(name) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}

/** Replaces [live] with [snapshot] using the same rename swap as backup restore. */
fun installSnapshot(live: File, snapshot: File, close: () -> Unit) {
    val swap = DatabaseSwap(live)
    swap.install(snapshot) { close() }
    swap.discardAside()
}
