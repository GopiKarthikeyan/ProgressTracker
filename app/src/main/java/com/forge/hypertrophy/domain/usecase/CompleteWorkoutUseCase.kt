package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.engine.DoubleProgressionEngine
import com.forge.hypertrophy.domain.engine.E1rmCalculator
import com.forge.hypertrophy.domain.engine.LiftSample
import com.forge.hypertrophy.domain.engine.LoadRounding
import com.forge.hypertrophy.domain.engine.PrDetector
import com.forge.hypertrophy.domain.engine.StartingWeight
import com.forge.hypertrophy.domain.engine.StaticSkillEngine
import com.forge.hypertrophy.domain.engine.slotSessionsForProgression
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SkillPosition
import com.forge.hypertrophy.domain.model.SkillStageTargets
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotSession
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.workout.WorkoutMachineState
import com.forge.hypertrophy.ui.screens.workout.NextSessionNote
import com.forge.hypertrophy.ui.screens.workout.PrNote
import com.forge.hypertrophy.ui.screens.workout.StagePrompt
import com.forge.hypertrophy.ui.screens.workout.WorkoutSummary
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class CompleteWorkoutUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val skills: SkillRepository,
    private val baselines: BaselineRepository,
    private val clock: Clock,
) {
    private val prs = PrDetector()
    private val progression = DoubleProgressionEngine()
    private val skillEngine = StaticSkillEngine()

    suspend fun execute(sessionId: Long, machine: WorkoutMachineState): WorkoutSummary {
        val session = sessions.get(sessionId) ?: throw IllegalArgumentException("Session not found")
        val now = clock.instant()
        
        val summary = summarize(sessionId, machine)
        
        sessions.update(session.copy(status = SessionStatus.COMPLETED, completedAt = now))
        sealCalibration(machine, now)
        
        return summary
    }

    private suspend fun summarize(sessionId: Long, machine: WorkoutMachineState): WorkoutSummary {
        val history = historySamples(sessionId)
        val prNotes = machine.slots.flatMap { slot ->
            slot.sets.mapNotNull { set ->
                val candidate = LiftSample(slot.activeExerciseId, slot.sessionSlotId, set.weightKg, set.reps)
                if (!prs.isNewPr(history, candidate)) return@mapNotNull null
                val estimate = E1rmCalculator.epley(set.weightKg ?: return@mapNotNull null, set.reps ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                PrNote(slot.exerciseName, LoadRounding.roundToDecimals(estimate))
            }
        }
        return WorkoutSummary(
            prs = prNotes,
            stagePrompts = stagePrompts(machine),
            nextSession = nextSessionNotes(sessionId, machine),
        )
    }

    private suspend fun historySamples(sessionId: Long): List<LiftSample> {
        val samples = mutableListOf<LiftSample>()
        sessions.allSlots().filter { it.sessionId != sessionId }.forEach { slot ->
            val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
            sessions.sets(slot.id).forEach { set ->
                samples += LiftSample(exerciseId, slot.id, set.weightKg, set.reps)
            }
        }
        return samples
    }

    private suspend fun stagePrompts(machine: WorkoutMachineState): List<StagePrompt> {
        val prompts = mutableListOf<StagePrompt>()
        machine.slots.filter { it.prescription.category == SlotCategory.SKILL && it.formConfirmed }.forEach { slot ->
            val exercise = exercises.get(slot.activeExerciseId) ?: return@forEach
            val skillId = exercise.skillId ?: return@forEach
            val steps = skills.getSteps(skillId)
            if (steps.isEmpty()) return@forEach
            val progress = skills.getProgress(skillId)
            val currentStep = steps.firstOrNull { it.id == progress?.currentStepId } ?: steps.first()
            val tier = steps.indexOf(currentStep).coerceAtLeast(0)
            val stage = progress?.stage ?: 1
            val total = slot.sets.sumOf { it.holdSec ?: 0 }
            val unbroken = slot.sets.maxOfOrNull { it.holdSec ?: 0 } ?: 0
            val next = skillEngine.afterAttempt(
                SkillPosition(tier, stage),
                SkillStageTargets(
                    currentStep.stage1TotalSec,
                    currentStep.stage2TotalLowSec,
                    currentStep.stage2TotalHighSec,
                    currentStep.stage3UnbrokenSec,
                ),
                total,
                unbroken,
                formConfirmed = true,
                tierCount = steps.size,
            )
            if (next.tierIndex == tier && next.stage == stage) return@forEach
            val step = steps.getOrElse(next.tierIndex) { steps.last() }
            val now = clock.instant()
            skills.upsertProgress(
                SkillProgressEntity(
                    id = progress?.id ?: 0,
                    skillId = skillId,
                    currentStepId = step.id,
                    stage = next.stage,
                    updatedAt = now,
                ),
            )
            skills.recordStageEvent(
                SkillStageEventEntity(
                    skillId = skillId,
                    date = now.atZone(clock.zone).toLocalDate(),
                    fromTier = tier,
                    fromStage = stage,
                    toTier = next.tierIndex,
                    toStage = next.stage,
                    recordedAt = now,
                ),
            )
            prompts += StagePrompt(skills.get(skillId)?.name.orEmpty(), next.tierIndex, next.stage)
        }
        return prompts
    }

    private suspend fun nextSessionNotes(sessionId: Long, machine: WorkoutMachineState): List<NextSessionNote> {
        val prior = sessions.completedSets().filter { it.sessionId != sessionId }
        return machine.slots.mapNotNull { slot ->
            val low = slot.prescription.repsLow ?: return@mapNotNull null
            val high = slot.prescription.repsHigh ?: return@mapNotNull null
            if (slot.sets.isEmpty()) return@mapNotNull null
            val exercise = exercises.get(slot.activeExerciseId)
            val routineId = slot.routineSlotId
            val stored = routineId?.let { baselines.forSlot(it) }
            val starting = stored?.let { StartingWeight(it.weightKg, it.repsHint, it.setAt) }
            val hint = machine.baselineBySessionSlot[slot.sessionSlotId]
            val awaiting = if (starting != null) starting.awaitingCalibration else hint?.awaitingCalibration == true
            val loggedWeight = slot.sets.mapNotNull { it.weightKg }.maxOrNull()
            val history = if (routineId == null) {
                emptyList()
            } else {
                slotSessionsForProgression(
                    prior.filter { it.slotId == routineId }
                        .groupBy { it.sessionId }
                        .values
                        .map { rows ->
                            rows.first().completedAt to rows.map { LoggedSet(it.weightKg, it.reps, it.setType) }
                        },
                    starting,
                )
            }
            val increment = exercise?.loadIncrementKg ?: 2.5
            val counted = listOf(
                SlotSession(
                    slot.sets.map { LoggedSet(it.weightKg, it.reps, SetType.WORKING) },
                    calibration = awaiting,
                ),
            ) + history
            val suggestion = progression.suggest(
                ProgressionInput(
                    rule = slot.prescription.progressionRule,
                    equipment = slot.equipment,
                    metricType = slot.prescription.metricType,
                    repsLow = low,
                    repsHigh = high,
                    exerciseIncrementKg = increment,
                    incrementOverrideKg = slot.prescription.incrementOverrideKg,
                    slotSessions = counted,
                    latestWeightFromAnySlotKg = loggedWeight,
                    baselineWeightKg = if (awaiting) loggedWeight else starting?.weightKg ?: hint?.weightKg,
                    awaitingCalibration = awaiting && loggedWeight == null,
                ),
            )
            NextSessionNote(slot.exerciseName, suggestion.action, suggestion.weightKg)
        }
    }

    private suspend fun sealCalibration(machine: WorkoutMachineState, at: Instant) {
        for (slot in machine.slots) {
            val routineId = slot.routineSlotId ?: continue
            val hint = machine.baselineBySessionSlot[slot.sessionSlotId] ?: continue
            if (!hint.awaitingCalibration) continue
            val best = slot.sets.filter { it.weightKg != null }.maxByOrNull { it.weightKg ?: 0.0 } ?: continue
            val existing = baselines.forSlot(routineId) ?: continue
            baselines.save(existing.copy(weightKg = best.weightKg, repsHint = best.reps, setAt = at))
        }
    }
}
