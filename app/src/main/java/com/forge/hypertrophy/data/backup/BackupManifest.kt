package com.forge.hypertrophy.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val mediaFiles: Int = 0,
)

class NewerBackupException(
    val schemaVersion: Int,
    val supportedVersion: Int,
) : Exception("Backup schema $schemaVersion is newer than supported schema $supportedVersion")

class ZipSlipException(val entryName: String) : Exception("Backup entry escapes the archive: $entryName")

class InvalidBackupException(message: String) : Exception(message)

fun BackupManifest.validate(currentSchema: Int) {
    if (appVersion.isBlank() || exportedAt.isBlank()) throw InvalidBackupException("Backup manifest is incomplete")
    if (schemaVersion > currentSchema) throw NewerBackupException(schemaVersion, currentSchema)
}
