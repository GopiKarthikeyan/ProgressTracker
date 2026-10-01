package com.forge.hypertrophy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * One delegate per file. A second delegate with the same name would open a
 * second DataStore on that file and crash.
 */
internal val Context.trainingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "training",
)

internal val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding",
)
