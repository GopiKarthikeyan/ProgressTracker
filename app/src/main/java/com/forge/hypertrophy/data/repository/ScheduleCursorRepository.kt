package com.forge.hypertrophy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ScheduleCursorRepository {
    val fixedSwaps: Flow<Map<LocalDate, Long>>

    val rollingDayByDate: Flow<Map<LocalDate, Long>>

    val autoCompletedRests: Flow<Set<LocalDate>>

    suspend fun save(
        fixedSwaps: Map<LocalDate, Long>,
        rollingDayByDate: Map<LocalDate, Long>,
        autoCompletedRests: Set<LocalDate>,
    )
}

@Singleton
class DataStoreScheduleCursorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScheduleCursorRepository {
    override val fixedSwaps: Flow<Map<LocalDate, Long>> = context.trainingDataStore.data.map { preferences ->
        decode(preferences[FixedSwapsKey])
    }

    override val rollingDayByDate: Flow<Map<LocalDate, Long>> = context.trainingDataStore.data.map { preferences ->
        decode(preferences[RollingDaysKey])
    }

    override val autoCompletedRests: Flow<Set<LocalDate>> = context.trainingDataStore.data.map { preferences ->
        preferences[AutoRestsKey]?.let { Json.decodeFromString<List<String>>(it) }
            ?.map(LocalDate::parse)
            ?.toSet()
            ?: emptySet()
    }

    override suspend fun save(
        fixedSwaps: Map<LocalDate, Long>,
        rollingDayByDate: Map<LocalDate, Long>,
        autoCompletedRests: Set<LocalDate>,
    ) {
        context.trainingDataStore.edit { preferences ->
            preferences[FixedSwapsKey] = encode(fixedSwaps)
            preferences[RollingDaysKey] = encode(rollingDayByDate)
            preferences[AutoRestsKey] = Json.encodeToString(autoCompletedRests.map { it.toString() }.sorted())
        }
    }

    private fun decode(raw: String?): Map<LocalDate, Long> {
        if (raw == null) return emptyMap()
        return Json.decodeFromString<List<DateDay>>(raw).associate { LocalDate.parse(it.date) to it.dayId }
    }

    private fun encode(days: Map<LocalDate, Long>): String {
        val encoded = days.entries
            .sortedBy { it.key }
            .map { DateDay(it.key.toString(), it.value) }
        return Json.encodeToString(encoded)
    }

    private companion object {
        val FixedSwapsKey = stringPreferencesKey("schedule_fixed_swaps")
        val RollingDaysKey = stringPreferencesKey("schedule_rolling_days")
        val AutoRestsKey = stringPreferencesKey("schedule_auto_rests")
    }
}

@Serializable
private data class DateDay(
    val date: String,
    val dayId: Long,
)
