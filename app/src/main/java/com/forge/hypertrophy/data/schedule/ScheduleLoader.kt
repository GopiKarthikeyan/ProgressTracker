package com.forge.hypertrophy.data.schedule

import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.TrainingDay
import com.forge.hypertrophy.domain.model.TrainingSlot
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class LoadedSchedule(
    val program: ProgramEntity,
    val days: List<RoutineDayEntity>,
    val slots: List<RoutineSlotEntity>,
    val snapshot: ScheduleSnapshot,
)

/**
 * Assembles the [ScheduleSnapshot] the use cases work on from the active
 * program, its days and slots, completed sessions, and the schedule cursor.
 * Shared by the dashboard and the home-screen widget so both read the same
 * schedule.
 */
class ScheduleLoader @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val preferences: TrainingPreferencesRepository,
    private val cursor: ScheduleCursorRepository,
) {
    suspend fun load(program: ProgramEntity? = null): LoadedSchedule? {
        val active = program ?: programs.observeActive().first() ?: return null
        val days = routines.days(active.id)
        val slots = routines.slotsForDays(days.map { it.id })
        val trainingDays = days.map { day ->
            TrainingDay(
                id = day.id,
                label = day.label,
                dayOfWeek = day.dayOfWeek?.let(DayOfWeek::of),
                sequenceIndex = day.sequenceIndex,
                isRest = day.isRest,
                slots = slots.filter { it.dayId == day.id }.sortedBy { it.sortOrder }.map { slot ->
                    TrainingSlot(slot.id, slot.toPrescription())
                },
            )
        }
        val autoRests = cursor.autoCompletedRests.first()
        val explicit = sessions.completedDays().map { it.date }.filter { it !in autoRests }.toSet()
        val snapshot = ScheduleSnapshot(
            mode = active.scheduleMode,
            days = trainingDays,
            rollingIndex = active.rollingSequence,
            lastReconciled = preferences.lastReconciledDate.first(),
            explicitCompletions = explicit,
            autoCompletedRests = autoRests,
            rollingDayByDate = cursor.rollingDayByDate.first(),
            fixedSwaps = cursor.fixedSwaps.first(),
        )
        return LoadedSchedule(active, days, slots, snapshot)
    }
}

fun RoutineSlotEntity.toPrescription() = SlotPrescription(
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
