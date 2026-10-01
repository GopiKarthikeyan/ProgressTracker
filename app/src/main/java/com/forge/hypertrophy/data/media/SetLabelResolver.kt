package com.forge.hypertrophy.data.media

import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.media.setLabel
import javax.inject.Inject

/** Builds the overlay text for a clip from the set it belongs to. */
class SetLabelResolver @Inject constructor(
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
) {
    suspend fun forSet(setEntryId: Long): String? {
        val set = sessions.getSet(setEntryId) ?: return null
        val slot = sessions.getSlot(set.sessionSlotId) ?: return null
        val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
        val exercise = exercises.get(exerciseId) ?: return null
        return setLabel(exercise.name, set.weightKg, set.reps, set.holdSec)
    }

    suspend fun forExercise(exerciseId: Long): String? = exercises.get(exerciseId)?.name
}
