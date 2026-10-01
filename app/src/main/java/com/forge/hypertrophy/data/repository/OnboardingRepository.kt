package com.forge.hypertrophy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface OnboardingRepository {
    val completed: Flow<Boolean>

    suspend fun markCompleted()
}

@Singleton
class DataStoreOnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingRepository {
    override val completed: Flow<Boolean> = context.onboardingDataStore.data.map { preferences ->
        preferences[CompletedKey] == true
    }

    override suspend fun markCompleted() {
        context.onboardingDataStore.edit { preferences ->
            preferences[CompletedKey] = true
        }
    }

    private companion object {
        val CompletedKey = booleanPreferencesKey("onboarding_complete")
    }
}
