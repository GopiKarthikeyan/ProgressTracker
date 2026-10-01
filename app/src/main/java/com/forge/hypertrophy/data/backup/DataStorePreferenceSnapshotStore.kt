package com.forge.hypertrophy.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.forge.hypertrophy.data.repository.onboardingDataStore
import com.forge.hypertrophy.data.repository.trainingDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Singleton
class DataStorePreferenceSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceSnapshotStore {
    override suspend fun capture(): PreferenceSnapshot = PreferenceSnapshot(
        files = mapOf(
            TRAINING to context.trainingDataStore.data.first().toStored(),
            ONBOARDING to context.onboardingDataStore.data.first().toStored(),
        ),
    )

    override suspend fun restore(snapshot: PreferenceSnapshot) {
        val training = snapshot.files[TRAINING] ?: throw InvalidBackupException("Backup is missing settings")
        val onboarding = snapshot.files[ONBOARDING] ?: throw InvalidBackupException("Backup is missing onboarding")
        context.trainingDataStore.replace(training)
        context.onboardingDataStore.replace(onboarding)
    }

    private companion object {
        const val TRAINING = "training"
        const val ONBOARDING = "onboarding"
    }
}

private fun Preferences.toStored(): List<StoredPreference> = asMap().map { (key, value) ->
    when (value) {
        is String -> StoredPreference(key.name, "string", value)
        is Int -> StoredPreference(key.name, "int", value.toString())
        is Long -> StoredPreference(key.name, "long", value.toString())
        is Boolean -> StoredPreference(key.name, "boolean", value.toString())
        is Float -> StoredPreference(key.name, "float", value.toString())
        is Double -> StoredPreference(key.name, "double", value.toString())
        is Set<*> -> StoredPreference(
            key.name,
            "string_set",
            Json.encodeToString(ListSerializer(String.serializer()), value.filterIsInstance<String>()),
        )
        else -> throw InvalidBackupException("Unsupported setting ${key.name}")
    }
}.sortedBy { it.key }

private suspend fun DataStore<Preferences>.replace(entries: List<StoredPreference>) {
    edit { preferences ->
        preferences.clear()
        entries.forEach { entry -> preferences.putStored(entry) }
    }
}

private fun MutablePreferences.putStored(entry: StoredPreference) {
    when (entry.type) {
        "string" -> this[stringPreferencesKey(entry.key)] = entry.value
        "int" -> this[intPreferencesKey(entry.key)] = entry.value.toInt()
        "long" -> this[longPreferencesKey(entry.key)] = entry.value.toLong()
        "boolean" -> this[booleanPreferencesKey(entry.key)] = entry.value.toBoolean()
        "float" -> this[floatPreferencesKey(entry.key)] = entry.value.toFloat()
        "double" -> this[doublePreferencesKey(entry.key)] = entry.value.toDouble()
        "string_set" -> this[stringSetPreferencesKey(entry.key)] =
            Json.decodeFromString(ListSerializer(String.serializer()), entry.value).toSet()
        else -> throw InvalidBackupException("Unsupported setting ${entry.key}")
    }
}
