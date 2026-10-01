package com.forge.hypertrophy.data.session

import androidx.room.withTransaction
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.engine.DoubleProgressionEngine
import com.forge.hypertrophy.domain.engine.LiftSample
import com.forge.hypertrophy.domain.engine.PrDetector
import com.forge.hypertrophy.domain.engine.StartingWeight
import com.forge.hypertrophy.domain.engine.slotSessionsForProgression
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.ProgressionSuggestion
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class SetEdit(
    val id: Long,
    val sessionSlotId: Long,
    val weightKg: Double?,
    val reps: Int?,
    val delete: Boolean = false,
    val create: Boolean = false,
)

data class SessionEdit(
    val date: LocalDate,
    val status: SessionStatus,
    val sets: List<SetEdit>,
)

data class SlotSuggestion(
    val exerciseName: String,
    val suggestion: ProgressionSuggestion,
)

data class SessionEditReport(
    val stageWarnings: List<String>,
    /** Best e1RM per exercise after the edit, recomputed from completed sets. */
    val bestE1rmKg: Map<Long, Double>,
    val suggestions: List<SlotSuggestion>,
)

/**
 * Writes a correction to a past session, then recomputes PRs and the next
 * suggestion for each affected slot. A confirmed skill-stage advance is left
 * in place; the report names it so the lifter can demote by hand.
 */
@Singleton
class SessionEditor @Inject constructor(
    private val database: AppDatabase,
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val skills: SkillRepository,
    private val baselines: BaselineRepository,
    private val clock: Clock,
) {
    private val prs = PrDetector()
    private val progression = DoubleProgressionEngine()

    suspend fun apply(sessionId: Long, edit: SessionEdit): SessionEditReport = database.withTransaction {
        val session = sessions.get(sessionId) ?: error("Missing session $sessionId")
        val originalDate = session.date
        val slots = sessions.observeSlots(sessionId).first()
        val skillIds = slots.mapNotNull { slot ->
            val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
            exercises.get(exerciseId)?.skillId
        }.toSet()
        val events = skills.stageEventsBetween(LocalDate.of(1970, 1, 1), LocalDate.of(2100, 12, 31))
        val warnings = events.filter { it.skillId in skillIds && it.date == originalDate }.map { event ->
            val name = skills.get(event.skillId)?.name ?: "Skill"
            "$name stage ${event.fromStage} → stage ${event.toStage} stays. Demote it manually if this session no longer earned it."
        }

        val completedAt = when {
            edit.status == SessionStatus.COMPLETED && session.completedAt == null -> clock.instant()
            else -> session.completedAt
        }
        sessions.update(
            session.copy(
                date = edit.date,
                status = edit.status,
                completedAt = completedAt,
                editedAt = clock.instant(),
            ),
        )
        for (change in edit.sets) {
            when {
                change.delete && change.id != 0L -> sessions.deleteSet(change.id)
                change.create -> sessions.insertSet(
                    SetEntryEntity(
                        sessionSlotId = change.sessionSlotId,
                        setNumber = nextSetNumber(change.sessionSlotId),
                        side = SetSide.BOTH,
                        setType = SetType.WORKING,
                        weightKg = change.weightKg,
                        reps = change.reps,
                        holdSec = null,
                        rpe = null,
                        jointFlags = emptyList(),
                        entryMethod = EntryMethod.SCREEN,
                        loggedAt = clock.instant(),
                    ),
                )
                change.id != 0L -> {
                    val existing = sessions.getSet(change.id) ?: continue
                    sessions.updateSet(existing.copy(weightKg = change.weightKg, reps = change.reps))
                }
            }
        }
        refreshCalibrationBaseline(sessionId, completedAt)

        val completed = sessions.completedSets()
        val lifts = completed.map { row ->
            val exerciseId = row.chosenAlternativeExerciseId ?: row.prescriptionSnapshot.exerciseId
            LiftSample(exerciseId, row.slotId ?: 0L, row.weightKg, row.reps)
        }
        SessionEditReport(
            stageWarnings = warnings,
            bestE1rmKg = prs.bestByExercise(lifts),
            suggestions = suggestions(slots, completed),
        )
    }

    private suspend fun nextSetNumber(sessionSlotId: Long): Int {
        val numbers = sessions.sets(sessionSlotId).map { it.setNumber }
        return (numbers.maxOrNull() ?: 0) + 1
    }

    private suspend fun refreshCalibrationBaseline(sessionId: Long, completedAt: Instant?) {
        if (completedAt == null) return
        for (slot in sessions.observeSlots(sessionId).first()) {
            val routineId = slot.slotId ?: continue
            val baseline = baselines.forSlot(routineId) ?: continue
            if (baseline.weightKg == null || baseline.setAt != completedAt) continue
            val working = sessions.sets(slot.id).filter { it.setType == SetType.WORKING || it.setType == SetType.AMRAP }
            val best = working.maxByOrNull { it.weightKg ?: 0.0 }
            baselines.save(
                baseline.copy(
                    weightKg = best?.weightKg,
                    repsHint = best?.reps,
                    setAt = baseline.setAt,
                ),
            )
        }
    }

    private suspend fun suggestions(
        slots: List<com.forge.hypertrophy.data.entity.SessionSlotEntity>,
        completed: List<com.forge.hypertrophy.data.dao.CompletedSetRow>,
    ): List<SlotSuggestion> {
        val notes = mutableListOf<SlotSuggestion>()
        for (slot in slots) {
            val routineId = slot.slotId ?: continue
            val routine = routines.getSlot(routineId) ?: continue
            val low = routine.repsLow ?: continue
            val high = routine.repsHigh ?: continue
            val exerciseId = slot.chosenAlternativeExerciseId ?: routine.exerciseId
            val exercise = exercises.get(exerciseId) ?: continue
            val baselineEntity = baselines.forSlot(routineId)
            val baseline = baselineEntity?.let { StartingWeight(it.weightKg, it.repsHint, it.setAt) }
            val history = slotSessionsForProgression(
                completed.filter { it.slotId == routineId }
                    .groupBy { it.sessionId }
                    .values
                    .map { rows ->
                        rows.first().completedAt to rows.map { LoggedSet(it.weightKg, it.reps, it.setType) }
                    },
                baseline,
            )
            val latest = completed
                .filter { (it.chosenAlternativeExerciseId ?: it.prescriptionSnapshot.exerciseId) == exerciseId }
                .filter { it.setType == SetType.WORKING && it.weightKg != null }
                .maxByOrNull { it.completedAt ?: Instant.EPOCH }
                ?.weightKg
            notes += SlotSuggestion(
                exerciseName = exercise.name,
                suggestion = progression.suggest(
                    ProgressionInput(
                        rule = routine.progressionRule,
                        equipment = exercise.equipment,
                        metricType = routine.metricType,
                        repsLow = low,
                        repsHigh = high,
                        exerciseIncrementKg = exercise.loadIncrementKg,
                        incrementOverrideKg = routine.incrementOverrideKg,
                        slotSessions = history,
                        latestWeightFromAnySlotKg = latest,
                        baselineWeightKg = baseline?.weightKg,
                        awaitingCalibration = baseline?.awaitingCalibration == true,
                    ),
                ),
            )
        }
        return notes
    }
}
