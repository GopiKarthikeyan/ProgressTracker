package com.forge.hypertrophy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface BackupPreferencesRepository {
    val enabled: Flow<Boolean>

    val treeUri: Flow<String?>

    val lastDate: Flow<LocalDate?>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTreeUri(uri: String?)

    suspend fun setLastDate(date: LocalDate?)
}

@Singleton
class DataStoreBackupPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupPreferencesRepository {
    override val enabled: Flow<Boolean> = context.trainingDataStore.data.map { preferences ->
        preferences[EnabledKey] == true
    }

    override val treeUri: Flow<String?> = context.trainingDataStore.data.map { preferences ->
        preferences[TreeUriKey]
    }

    override val lastDate: Flow<LocalDate?> = context.trainingDataStore.data.map { preferences ->
        preferences[LastDateKey]?.let(LocalDate::parse)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.trainingDataStore.edit { preferences -> preferences[EnabledKey] = enabled }
    }

    override suspend fun setTreeUri(uri: String?) {
        context.trainingDataStore.edit { preferences ->
            if (uri == null) preferences.remove(TreeUriKey) else preferences[TreeUriKey] = uri
        }
    }

    override suspend fun setLastDate(date: LocalDate?) {
        context.trainingDataStore.edit { preferences ->
            if (date == null) preferences.remove(LastDateKey) else preferences[LastDateKey] = date.toString()
        }
    }

    private companion object {
        val EnabledKey = booleanPreferencesKey("auto_backup_enabled")
        val TreeUriKey = stringPreferencesKey("auto_backup_tree_uri")
        val LastDateKey = stringPreferencesKey("last_auto_backup_date")
    }
}
