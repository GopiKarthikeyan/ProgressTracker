package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.domain.model.ChecklistPhase
import java.time.DayOfWeek

/**
 * Rebuilds a program document from the database.
 *
 * `sourceVersion` is always `"v1"` because the column does not exist.
 * The initial skill step is the step with the lowest order, because that
 * choice is not stored.
 * `defaults.barWeightKg` is the empty-bar convention of 20 kg; each exercise
 * stores its own bar. Transition rest and plates come from [preferences].
 * Superset group 1 is the letter A.
 */
class ProgramExporter(
    private val database: AppDatabase,
    private val preferences: ProgramExportPreferences = ProgramExportPreferences.Builtin,
) {
    suspend fun export(programId: Long): ProgramJson {
        val program = database.programDao().getById(programId)
            ?: error("program $programId does not exist")
        val days = database.routineDao().days(programId)
        val dayIds = days.map { it.id }
        val slots = if (dayIds.isEmpty()) emptyList() else database.routineDao().slotsForDays(dayIds)
        val slotIds = slots.map { it.id }
        val checklist = if (dayIds.isEmpty()) emptyList() else database.routineDao().checklistForDays(dayIds)
        val alternatives = if (slotIds.isEmpty()) {
            emptyList()
        } else {
            database.routineDao().alternativesForSlots(slotIds)
        }
        val cardio = if (dayIds.isEmpty()) emptyList() else database.routineDao().cardioForDays(dayIds)
        val exerciseIds = (slots.map { it.exerciseId } + alternatives.map { it.exerciseId }).distinct()
        val exercises = if (exerciseIds.isEmpty()) emptyList() else database.exerciseDao().getAll(exerciseIds)
        val skillIds = exercises.mapNotNull { it.skillId }.distinct()
        val skills = database.skillDao().all().filter { it.id in skillIds }
        val stepsBySkill = skills.associate { skill -> skill.id to database.skillDao().getSteps(skill.id) }
        val stepIds = (slots.mapNotNull { it.targetSkillStepId } + stepsBySkill.values.flatten().map { it.id })
            .distinct()
        val steps = if (stepIds.isEmpty()) emptyList() else database.skillDao().stepsByIds(stepIds)
        val exerciseKeys = KeyAllocator()
        val skillKeys = KeyAllocator()
        val stepKeys = KeyAllocator()
        val dayKeys = KeyAllocator()
        val slotKeys = KeyAllocator()
        val exerciseKeyById = exercises.associate { it.id to exerciseKeys.key(it.name) }
        val skillKeyById = skills.associate { it.id to skillKeys.key(it.name) }
        val stepKeyById = steps.associate { it.id to stepKeys.key(it.name) }
        val defaults = preferences.currentDefaults()
        return ProgramJson(
            schemaVersion = 1,
            program = ProgramJsonHeader(name = program.name, sourceVersion = "v1"),
            defaults = defaults,
            skills = skills.mapNotNull { skill ->
                val ordered = stepsBySkill.getValue(skill.id)
                if (ordered.isEmpty()) {
                    null
                } else {
                    ProgramJsonSkill(
                        key = skillKeyById.getValue(skill.id),
                        name = skill.name,
                        initialStepKey = stepKeyById.getValue(ordered.minBy { it.sortOrder }.id),
                        steps = ordered.map { step -> step.toJson(stepKeyById) },
                    )
                }
            },
            exercises = exercises.map { exercise -> exercise.toJson(exerciseKeyById, skillKeyById) },
            days = days.map { day ->
                val daySlots = slots.filter { it.dayId == day.id }
                val dayItems = checklist.filter { it.dayId == day.id }
                ProgramJsonDay(
                    key = dayKeys.key(day.label),
                    label = day.label,
                    dayOfWeek = day.dayOfWeek?.let { DayOfWeek.of(it).name },
                    sequenceIndex = day.sequenceIndex,
                    isRest = day.isRest,
                    prep = checklistJson(day.prepDurationMin, dayItems, ChecklistPhase.PREP),
                    cooldown = checklistJson(day.cooldownDurationMin, dayItems, ChecklistPhase.COOLDOWN),
                    slots = daySlots.map { slot ->
                        slot.toJson(slotKeys, exerciseKeyById, stepKeyById, alternatives)
                    },
                    cardio = cardio.filter { it.dayId == day.id }.map { plan ->
                        ProgramJsonCardio(
                            activity = plan.activity.name,
                            style = plan.type.name,
                            label = plan.label,
                            targetDistanceM = plan.targetDistanceM,
                            isOptional = plan.isOptional,
                        )
                    },
                )
            },
        )
    }

    private fun checklistJson(
        durationMin: Int?,
        items: List<ChecklistItemEntity>,
        phase: ChecklistPhase,
    ): ProgramJsonChecklist? {
        val phaseItems = items.filter { it.phase == phase }
        if (durationMin == null && phaseItems.isEmpty()) return null
        return ProgramJsonChecklist(
            durationMin = durationMin,
            items = phaseItems.map { item ->
                ProgramJsonChecklistItem(text = item.text, reps = item.reps, seconds = item.seconds)
            },
        )
    }

    private fun ExerciseEntity.toJson(
        exerciseKeyById: Map<Long, String>,
        skillKeyById: Map<Long, String>,
    ) = ProgramJsonExercise(
        key = exerciseKeyById.getValue(id),
        name = name,
        equipment = equipment.name,
        barWeightKg = barWeightKg,
        loadIncrementKg = loadIncrementKg,
        isUnilateral = isUnilateral,
        skillKey = skillId?.let { skillKeyById[it] },
        primaryMuscleGroups = primaryMuscleGroups,
        secondaryMuscleGroups = secondaryMuscleGroups,
        setupNotes = setupNotes.ifEmpty { null },
    )

    private fun SkillStepEntity.toJson(stepKeyById: Map<Long, String>) = ProgramJsonSkillStep(
        key = stepKeyById.getValue(id),
        name = name,
        order = sortOrder,
        stage1TotalSec = stage1TotalSec,
        stage2TotalLowSec = stage2TotalLowSec,
        stage2TotalHighSec = stage2TotalHighSec,
        stage3UnbrokenSec = stage3UnbrokenSec,
    )

    private fun RoutineSlotEntity.toJson(
        slotKeys: KeyAllocator,
        exerciseKeyById: Map<Long, String>,
        stepKeyById: Map<Long, String>,
        alternatives: List<SlotAlternativeEntity>,
    ) = ProgramJsonSlot(
        key = slotKeys.key("slot"),
        exerciseKey = exerciseKeyById.getValue(exerciseId),
        category = category.name,
        metricType = metricType.name,
        setsMin = setsMin,
        setsMax = setsMax,
        repsLow = repsLow,
        repsHigh = repsHigh,
        isAmrap = isAmrap,
        holdTargetSec = holdTargetSec,
        holdTargetMaxSec = holdTargetMaxSec,
        blockDurationSec = blockDurationSec,
        restMinSec = restMinSec,
        restMaxSec = restMaxSec,
        restAsNeeded = restAsNeeded,
        isOptional = isOptional,
        skipReasonLabel = skipReasonLabel,
        supersetGroup = supersetLetter(supersetGroup),
        targetSkillStepKey = targetSkillStepId?.let { stepKeyById[it] },
        progressionRule = progressionRule.name,
        incrementOverrideKg = incrementOverrideKg,
        alternativeExerciseKeys = alternatives
            .filter { it.slotId == id }
            .map { exerciseKeyById.getValue(it.exerciseId) },
        notes = notes,
        order = sortOrder,
    )
}

fun interface ProgramExportPreferences {
    suspend fun currentDefaults(): ProgramJsonDefaults

    companion object {
        val Builtin: ProgramExportPreferences = ProgramExportPreferences {
            ProgramJsonDefaults(
                transitionRestSec = 120,
                barWeightKg = 20.0,
                plateInventoryKg = emptyList(),
            )
        }
    }
}

private class KeyAllocator {
    private val used = mutableSetOf<String>()

    fun key(name: String): String {
        val base = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "item" }
        var candidate = base
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "${base}_$suffix"
            suffix += 1
        }
        return candidate
    }
}
