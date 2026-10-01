package com.forge.hypertrophy.data.backup

import com.forge.hypertrophy.data.repository.BackupPreferencesRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyAutoBackupTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun skipsWhenDisabledOrRecent() = runBlocking {
        val preferences = FakeBackupPreferences()
        val writer = RecordingWriter()
        val backup = WeeklyAutoBackup(preferences, clock, writer)
        backup.runIfDue()
        preferences.enabledState.value = true
        backup.runIfDue()
        preferences.uriState.value = "content://folder"
        preferences.lastState.value = LocalDate.of(2026, 9, 25)
        backup.runIfDue()
        assertTrue(writer.names.isEmpty())
        assertEquals(LocalDate.of(2026, 9, 25), preferences.lastState.value)
    }

    @Test
    fun writesWhenAWeekHasPassedAndRecordsTheDate() = runBlocking {
        val preferences = FakeBackupPreferences().apply {
            enabledState.value = true
            uriState.value = "content://folder"
            lastState.value = LocalDate.of(2026, 9, 24)
        }
        val writer = RecordingWriter()
        WeeklyAutoBackup(preferences, clock, writer).runIfDue()
        assertEquals(listOf("hypertrophy-backup-2026-10-01.zip"), writer.names)
        assertEquals(LocalDate.of(2026, 10, 1), preferences.lastState.value)
    }

    @Test
    fun doesNotAdvanceTheDateWhenWritingFails() = runBlocking {
        val preferences = FakeBackupPreferences().apply {
            enabledState.value = true
            uriState.value = "content://folder"
        }
        val writer = BackupWriter { _, _ -> error("folder unavailable") }
        try {
            WeeklyAutoBackup(preferences, clock, writer).runIfDue()
            error("failure was ignored")
        } catch (error: IllegalStateException) {
            assertEquals("folder unavailable", error.message)
        }
        assertEquals(null, preferences.lastState.value)
    }

    private class RecordingWriter : BackupWriter {
        val names = mutableListOf<String>()
        override suspend fun write(treeUri: String, displayName: String) {
            names += displayName
        }
    }

    private class FakeBackupPreferences : BackupPreferencesRepository {
        val enabledState = MutableStateFlow(false)
        val uriState = MutableStateFlow<String?>(null)
        val lastState = MutableStateFlow<LocalDate?>(null)
        override val enabled: Flow<Boolean> = enabledState
        override val treeUri: Flow<String?> = uriState
        override val lastDate: Flow<LocalDate?> = lastState
        override suspend fun setEnabled(enabled: Boolean) {
            enabledState.value = enabled
        }
        override suspend fun setTreeUri(uri: String?) {
            uriState.value = uri
        }
        override suspend fun setLastDate(date: LocalDate?) {
            lastState.value = date
        }
    }
}
