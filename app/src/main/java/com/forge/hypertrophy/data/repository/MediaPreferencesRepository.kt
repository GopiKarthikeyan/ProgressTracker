package com.forge.hypertrophy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.forge.hypertrophy.domain.media.DEFAULT_LEAD_TRIM_MS
import com.forge.hypertrophy.domain.media.DEFAULT_TAIL_TRIM_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Clip trim settings. Stored in the training DataStore so backups carry them. */
interface MediaPreferencesRepository {
    val leadTrimMs: Flow<Long>

    val tailTrimMs: Flow<Long>

    suspend fun setLeadTrimMs(ms: Long)

    suspend fun setTailTrimMs(ms: Long)
}

@Singleton
class DataStoreMediaPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaPreferencesRepository {
    override val leadTrimMs: Flow<Long> = context.trainingDataStore.data.map { preferences ->
        preferences[LeadTrimKey] ?: DEFAULT_LEAD_TRIM_MS
    }

    override val tailTrimMs: Flow<Long> = context.trainingDataStore.data.map { preferences ->
        preferences[TailTrimKey] ?: DEFAULT_TAIL_TRIM_MS
    }

    override suspend fun setLeadTrimMs(ms: Long) {
        context.trainingDataStore.edit { preferences -> preferences[LeadTrimKey] = ms.coerceAtLeast(0) }
    }

    override suspend fun setTailTrimMs(ms: Long) {
        context.trainingDataStore.edit { preferences -> preferences[TailTrimKey] = ms.coerceAtLeast(0) }
    }

    private companion object {
        val LeadTrimKey = longPreferencesKey("clip_lead_trim_ms")
        val TailTrimKey = longPreferencesKey("clip_tail_trim_ms")
    }
}
