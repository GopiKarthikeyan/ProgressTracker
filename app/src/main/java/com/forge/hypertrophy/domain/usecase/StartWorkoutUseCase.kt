package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

class StartWorkoutUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val programs: ProgramRepository,
    private val clock: Clock,
) {
    suspend fun execute(dayId: Long): Long {
        val day = routines.getDay(dayId) ?: throw IllegalArgumentException("Day not found")
        val program = programs.getById(day.programId)
        val id = sessions.insert(
            WorkoutSessionEntity(
                date = clock.instant().atZone(clock.zone).toLocalDate(),
                dayId = dayId,
                kind = SessionKind.GYM,
                status = SessionStatus.PLANNED,
                isDeload = program?.deloadActive == true,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = null,
                completedAt = null,
            ),
        )
        routines.observeSlots(dayId).first().forEach { slot ->
            sessions.insertSlot(
                SessionSlotEntity(
                    sessionId = id,
                    slotId = slot.id,
                    prescriptionSnapshot = slot.toSnapshot(),
                    chosenAlternativeExerciseId = null,
                    skipped = false,
                    skipReason = null,
                    formConfirmed = null,
                ),
            )
        }
        return id
    }

    private fun com.forge.hypertrophy.data.entity.RoutineSlotEntity.toSnapshot() = com.forge.hypertrophy.domain.model.SlotPrescription(
        exerciseId = exerciseId,
        category = category,
        sortOrder = sortOrder,
        supersetGroup = supersetGroup,
        metricType = metricType,
        setsMin = setsMin,
        setsMax = setsMax,
        repsLow = repsLow,
        repsHigh = repsHigh,
        isAmrap = isAmrap,
        holdTargetSec = holdTargetSec,
        blockDurationSec = blockDurationSec,
        restMinSec = restMinSec,
        restMaxSec = restMaxSec,
        restAsNeeded = restAsNeeded,
        isOptional = isOptional,
        skipReasonLabel = skipReasonLabel,
        targetSkillStepId = targetSkillStepId,
        progressionRule = progressionRule,
        incrementOverrideKg = incrementOverrideKg,
        notes = notes,
        holdTargetMaxSec = holdTargetMaxSec,
    )
}
