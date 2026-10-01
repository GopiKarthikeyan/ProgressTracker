package com.forge.hypertrophy.data.backup

import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.ProgramJsonDefaults
import com.forge.hypertrophy.data.transfer.ProgramJsonHeader
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun archiveStreamsTheDatabaseAndSettings() {
        val database = folder.newFile("source.db").apply { writeText("database-bytes") }
        val zip = folder.newFile("backup.zip")
        val programs = listOf(
            ProgramJson(
                schemaVersion = 1,
                program = ProgramJsonHeader(name = "Hypertrophy"),
                defaults = ProgramJsonDefaults(transitionRestSec = 120, barWeightKg = 20.0, plateInventoryKg = emptyList()),
            ),
        )
        val preferences = PreferenceSnapshot(
            files = mapOf("training" to listOf(StoredPreference("plate_inventory_kg", "string", "[20.0]"))),
        )
        zip.outputStream().use { output ->
            BackupArchive.write(
                output,
                BackupManifest(schemaVersion = 1, appVersion = "0.1.0", exportedAt = "2026-10-01T00:00:00Z"),
                database,
                programs,
                preferences,
            )
        }
        val extracted = folder.newFolder("extracted")
        zip.inputStream().use { BackupArchive.extract(it, extracted) }
        assertEquals("database-bytes", File(extracted, BackupArchive.DATABASE).readText())
        assertEquals(1, BackupArchive.readManifest(extracted).schemaVersion)
        assertEquals("0.1.0", BackupArchive.readManifest(extracted).appVersion)
        assertTrue(File(extracted, BackupArchive.PROGRAMS).readText().contains("Hypertrophy"))
        assertEquals("[20.0]", BackupArchive.readPreferences(extracted).files.getValue("training").single().value)
    }

    @Test
    fun zipSlipIsRejectedBeforeExtraction() {
        val zip = folder.newFile("slip.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("../escape.txt"))
            output.write("nope".toByteArray())
            output.closeEntry()
        }
        val destination = folder.newFolder("destination")
        try {
            zip.inputStream().use { BackupArchive.extract(it, destination) }
            error("zip slip was accepted")
        } catch (error: ZipSlipException) {
            assertTrue(error.entryName.contains(".."))
        }
        assertFalse(File(folder.root, "escape.txt").exists())
        assertFalse(File(destination, "escape.txt").exists())
    }

    @Test
    fun mediaEntriesStayOneLevelDeepAndReportProgress() {
        val database = folder.newFile("source.db").apply { writeText("db") }
        val clip = folder.newFile("clip-9.mp4").apply { writeText("clip-bytes") }
        val zip = folder.newFile("media.zip")
        val seen = mutableListOf<BackupProgress>()
        zip.outputStream().use { output ->
            BackupArchive.write(
                output,
                BackupManifest(schemaVersion = 1, appVersion = "0.1.0", exportedAt = "2026-10-01T00:00:00Z", mediaFiles = 1),
                database,
                emptyList(),
                PreferenceSnapshot(files = emptyMap()),
                media = listOf(clip),
                onProgress = { seen += it },
            )
        }
        assertEquals(database.length() + clip.length(), seen.last().bytesTotal)
        assertEquals(seen.last().bytesTotal, seen.last().bytesDone)
        val extracted = folder.newFolder("media-extracted")
        zip.inputStream().use { BackupArchive.extract(it, extracted) }
        assertEquals("clip-bytes", File(extracted, "media/clip-9.mp4").readText())
        assertEquals(1, BackupArchive.readManifest(extracted).mediaFiles)
    }

    @Test
    fun nestedMediaEntryIsRejected() {
        val zip = folder.newFile("nested.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("media/deeper/clip.mp4"))
            output.write("nope".toByteArray())
            output.closeEntry()
        }
        val destination = folder.newFolder("nested-destination")
        try {
            zip.inputStream().use { BackupArchive.extract(it, destination) }
            error("nested media entry was accepted")
        } catch (error: InvalidBackupException) {
            assertTrue(error.message!!.contains("media/deeper/clip.mp4"))
        }
        assertFalse(File(destination, "media/deeper/clip.mp4").exists())
    }

    @Test
    fun newerManifestIsRefused() {
        val manifest = BackupManifest(schemaVersion = 99, appVersion = "9.0.0", exportedAt = "2026-10-01T00:00:00Z")
        try {
            manifest.validate(currentSchema = 1)
            error("newer schema was accepted")
        } catch (error: NewerBackupException) {
            assertEquals(99, error.schemaVersion)
            assertEquals(1, error.supportedVersion)
        }
    }
}
