package com.forge.hypertrophy.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class PreferenceSnapshot(
    val files: Map<String, List<StoredPreference>> = emptyMap(),
)

@Serializable
data class StoredPreference(
    val key: String,
    val type: String,
    val value: String,
)

interface PreferenceSnapshotStore {
    suspend fun capture(): PreferenceSnapshot

    suspend fun restore(snapshot: PreferenceSnapshot)
}
