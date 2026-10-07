package com.forge.hypertrophy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class DataStoreTrainingPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrainingPreferencesRepository {
    override val lastReconciledDate: Flow<LocalDate?> = context.trainingDataStore.data.map { preferences ->
        preferences[LastReconciledDateKey]?.let(LocalDate::parse)
    }

    override val plateInventoryKg: Flow<List<Double>> = context.trainingDataStore.data.map { preferences ->
        preferences[PlateInventoryKey]?.let { Json.decodeFromString<List<Double>>(it) } ?: emptyList()
    }

    override val transitionRestSeconds: Flow<Int> = context.trainingDataStore.data.map { preferences ->
        preferences[TransitionRestKey] ?: DEFAULT_TRANSITION_REST_SECONDS
    }

    override val activeTimerEndElapsedRealtime: Flow<Long?> = context.trainingDataStore.data.map { preferences ->
        preferences[ActiveTimerEndKey]
    }

    override val defaultRestSeconds: Flow<Int> = context.trainingDataStore.data.map { preferences ->
        preferences[DefaultRestKey] ?: DEFAULT_REST_SECONDS
    }

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        context.trainingDataStore.edit { preferences ->
            preferences[DefaultRestKey] = seconds
        }
    }

    override suspend fun setLastReconciledDate(date: LocalDate?) {
        context.trainingDataStore.edit { preferences ->
            if (date == null) {
                preferences.remove(LastReconciledDateKey)
            } else {
                preferences[LastReconciledDateKey] = date.toString()
            }
        }
    }

    override suspend fun setPlateInventoryKg(platesKg: List<Double>) {
        context.trainingDataStore.edit { preferences ->
            preferences[PlateInventoryKey] = Json.encodeToString(platesKg)
        }
    }

    override suspend fun setTransitionRestSeconds(seconds: Int) {
        context.trainingDataStore.edit { preferences ->
            preferences[TransitionRestKey] = seconds
        }
    }

    override suspend fun setActiveTimerEndElapsedRealtime(elapsedRealtime: Long?) {
        context.trainingDataStore.edit { preferences ->
            if (elapsedRealtime == null) {
                preferences.remove(ActiveTimerEndKey)
            } else {
                preferences[ActiveTimerEndKey] = elapsedRealtime
            }
        }
    }

    private companion object {
        const val DEFAULT_TRANSITION_REST_SECONDS = 120
        const val DEFAULT_REST_SECONDS = 90
        val LastReconciledDateKey = stringPreferencesKey("last_reconciled_date")
        val DefaultRestKey = intPreferencesKey("default_rest_seconds")
        val PlateInventoryKey = stringPreferencesKey("plate_inventory_kg")
        val TransitionRestKey = intPreferencesKey("transition_rest_seconds")
        val ActiveTimerEndKey = longPreferencesKey("active_timer_end_elapsed_realtime")
    }
}
