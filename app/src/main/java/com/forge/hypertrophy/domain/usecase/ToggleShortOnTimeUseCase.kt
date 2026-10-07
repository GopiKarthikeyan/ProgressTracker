package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.engine.SessionEstimator
import com.forge.hypertrophy.domain.engine.ShortOnTimePlanner
import com.forge.hypertrophy.domain.model.TrainingSlot
import com.forge.hypertrophy.domain.workout.WorkoutMachineState
import javax.inject.Inject

class ToggleShortOnTimeUseCase @Inject constructor(
    private val sessions: SessionRepository,
) {
    private val estimator = SessionEstimator()
    private val planner = ShortOnTimePlanner(estimator)

    suspend fun execute(
        sessionId: Long,
        machine: WorkoutMachineState,
        originalBounds: Map<Long, Pair<Int, Int>>
    ): WorkoutMachineState {
        val session = sessions.get(sessionId) ?: return machine
        val enabling = !session.isShortOnTime
        
        val newMachine = if (enabling) {
            val pending = machine.slots.filter { it.sets.isEmpty() && !it.skipped }
            val asTraining = pending.map { TrainingSlot(it.sessionSlotId, it.prescription) }
            val estimate = estimator.estimate(asTraining, machine.transitionRestSeconds)
            val planned = planner.plan(asTraining, (estimate / 2).coerceAtLeast(1), machine.transitionRestSeconds)
            val plannedById = planned.associateBy { it.id }
            machine.copy(
                slots = machine.slots.map { slot ->
                    if (slot.sets.isNotEmpty() || slot.skipped) return@map slot
                    val kept = plannedById[slot.sessionSlotId]
                    if (kept == null) {
                        slot.copy(skipped = true, skipReason = "short on time")
                    } else {
                        slot.copy(prescription = kept.prescription)
                    }
                },
            )
        } else {
            machine.copy(
                slots = machine.slots.map { slot ->
                    val bounds = originalBounds[slot.sessionSlotId]
                    val restored = if (bounds == null) {
                        slot.prescription
                    } else {
                        slot.prescription.copy(setsMin = bounds.first, setsMax = bounds.second)
                    }
                    if (slot.skipReason == "short on time") {
                        slot.copy(skipped = false, skipReason = null, prescription = restored)
                    } else {
                        slot.copy(prescription = restored)
                    }
                },
            )
        }
        
        sessions.update(session.copy(isShortOnTime = enabling))
        return newMachine
    }
}
