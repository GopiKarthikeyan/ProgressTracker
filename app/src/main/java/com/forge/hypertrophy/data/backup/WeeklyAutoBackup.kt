package com.forge.hypertrophy.data.backup

import com.forge.hypertrophy.data.repository.BackupPreferencesRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

fun interface BackupWriter {
    suspend fun write(treeUri: String, displayName: String)
}

class WeeklyAutoBackup @Inject constructor(
    private val preferences: BackupPreferencesRepository,
    private val clock: Clock,
    private val writer: BackupWriter,
) {
    suspend fun runIfDue() {
        if (!preferences.enabled.first()) return
        val uri = preferences.treeUri.first() ?: return
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val last = preferences.lastDate.first()
        if (last != null && ChronoUnit.DAYS.between(last, today) < INTERVAL_DAYS) return
        writer.write(uri, "hypertrophy-backup-$today.zip")
        preferences.setLastDate(today)
    }

    private companion object {
        const val INTERVAL_DAYS = 7L
    }
}
