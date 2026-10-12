package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.workout.RecordedSet
import com.forge.hypertrophy.domain.workout.SetSuggestion
import java.time.Clock
import javax.inject.Inject

/**
 * Set inserts go through Room's suspend API (already off the main thread via
 * Room's query executor). Avoid [kotlinx.coroutines.Dispatchers.IO] here so
 * unit tests on a paused [kotlinx.coroutines.test.StandardTestDispatcher] can
 * advance without hanging.
 */
class LogSetUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val clock: Clock,
) {
    suspend fun logWorkingSet(
        sessionSlotId: Long,
        setNumber: Int,
        side: SetSide,
        suggestion: SetSuggestion,
        jointFlags: List<String>,
        method: EntryMethod
    ): RecordedSet {
        val id = sessions.insertSet(
            SetEntryEntity(
                sessionSlotId = sessionSlotId,
                setNumber = setNumber,
                side = side,
                setType = SetType.WORKING,
                weightKg = suggestion.weightKg,
                reps = suggestion.reps,
                holdSec = suggestion.holdSec,
                rpe = null,
                jointFlags = jointFlags,
                entryMethod = method,
                loggedAt = clock.instant(),
            ),
        )
        return RecordedSet(
            id = id,
            setNumber = setNumber,
            side = side,
            weightKg = suggestion.weightKg,
            reps = suggestion.reps,
            holdSec = suggestion.holdSec,
            rpe = null,
            jointFlags = jointFlags,
            entryMethod = method,
        )
    }

    suspend fun logPracticeBlock(
        sessionSlotId: Long,
        elapsedSeconds: Int
    ): RecordedSet {
        val id = sessions.insertSet(
            SetEntryEntity(
                sessionSlotId = sessionSlotId,
                setNumber = 1,
                side = SetSide.BOTH,
                setType = SetType.WORKING,
                weightKg = null,
                reps = null,
                holdSec = elapsedSeconds,
                rpe = null,
                jointFlags = emptyList(),
                entryMethod = EntryMethod.SCREEN,
                loggedAt = clock.instant(),
            ),
        )
        return RecordedSet(
            id = id,
            setNumber = 1,
            side = SetSide.BOTH,
            weightKg = null,
            reps = null,
            holdSec = elapsedSeconds,
            rpe = null,
            jointFlags = emptyList(),
            entryMethod = EntryMethod.SCREEN,
        )
    }
}
