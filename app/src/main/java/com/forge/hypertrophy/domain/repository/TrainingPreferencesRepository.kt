package com.forge.hypertrophy.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TrainingPreferencesRepository {
    val lastReconciledDate: Flow<LocalDate?>

    val plateInventoryKg: Flow<List<Double>>

    val transitionRestSeconds: Flow<Int>

    val activeTimerEndElapsedRealtime: Flow<Long?>

    /** Rest the Quick Settings tile starts, in seconds. */
    val defaultRestSeconds: Flow<Int>

    suspend fun setLastReconciledDate(date: LocalDate?)

    suspend fun setPlateInventoryKg(platesKg: List<Double>)

    suspend fun setTransitionRestSeconds(seconds: Int)

    suspend fun setActiveTimerEndElapsedRealtime(elapsedRealtime: Long?)

    suspend fun setDefaultRestSeconds(seconds: Int)
}
