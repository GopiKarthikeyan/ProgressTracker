package com.forge.hypertrophy.data.transfer

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SlotCategory
import java.time.Clock
import java.time.DayOfWeek

fun interface ProgramImportPreferences {
    suspend fun applyDefaults(transitionRestSeconds: Int, plateInventoryKg: List<Double>)

    companion object {
        val None: ProgramImportPreferences = ProgramImportPreferences { _, _ -> }
    }
}

class TrainingPreferencesImportDefaults(
    private val preferences: TrainingPreferencesRepository,
) : ProgramImportPreferences {
    override suspend fun applyDefaults(transitionRestSeconds: Int, plateInventoryKg: List<Double>) {
        preferences.setTransitionRestSeconds(transitionRestSeconds)
        preferences.setPlateInventoryKg(plateInventoryKg)
    }
}

sealed interface ImportResult {
    data class Imported(val programId: Long, val preview: ImportPreview) : ImportResult

    data class Rejected(val preview: ImportPreview) : ImportResult
}

/**
 * Writes a program document in one [AppDatabase.withTransaction]. A throw leaves
 * no rows. Transition rest and plate inventory are written through
 * [ProgramImportPreferences] only after that transaction commits.
 *
 * Exercises and skills merge by trimmed, case-insensitive name so logged
 * history keeps its ids. [ImportMode.REPLACE_EVERYTHING] then removes library
 * rows the document does not name.
 *
 * `sourceVersion` is accepted and not stored. There is no column for it.
 */
class ProgramImporter(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val preferences: ProgramImportPreferences = ProgramImportPreferences.None,
    private val beforeCommit: suspend () -> Unit = {},
) {
    suspend fun preview(
        document: ProgramJson,
        mode: ImportMode,
        scheduleMode: ScheduleMode = ScheduleMode.FIXED,
    ): ImportPreview {
        val report = ProgramJsonValidator.validate(document, scheduleMode)
        val exercises = database.exerciseDao().all()
        val skills = database.skillDao().all()
        val exerciseNames = exercises.map { libraryKey(it.name) }.toSet()
        val skillNames = skills.map { libraryKey(it.name) }.toSet()
        val importedExerciseNames = document.exercises.map { libraryKey(it.name) }.toSet()
        val archiveNames = if (mode == ImportMode.REPLACE_EVERYTHING) {
            val referenced = referencedExerciseIds(ignoringProgramId = targetProgramId(mode))
            exercises
                .filter { it.archivedAt == null }
                .filter { libraryKey(it.name) !in importedExerciseNames }
                .filter { it.id in referenced }
                .map { it.name }
        } else {
            emptyList()
        }
        return ImportPreview(
            programName = document.program.name,
            dayCount = document.days.size,
            slotCount = document.days.sumOf { it.slots.size },
            newExerciseNames = document.exercises
                .filter { libraryKey(it.name) !in exerciseNames }
                .map { it.name },
            newSkillNames = document.skills
                .filter { libraryKey(it.name) !in skillNames }
                .map { it.name },
            exercisesToArchive = archiveNames,
            errors = report.errors,
            warnings = report.warnings,
        )
    }

    suspend fun import(
        document: ProgramJson,
        mode: ImportMode,
        scheduleMode: ScheduleMode = ScheduleMode.FIXED,
        applyDefaults: Boolean = true,
    ): ImportResult {
        val preview = preview(document, mode, scheduleMode)
        if (preview.errors.isNotEmpty()) return ImportResult.Rejected(preview)
        val programId = database.runTransaction {
            val id = write(document, mode, scheduleMode)
            beforeCommit()
            id
        }
        if (applyDefaults) {
            preferences.applyDefaults(
                document.defaults.transitionRestSec,
                document.defaults.plateInventoryKg,
            )
        }
        return ImportResult.Imported(programId, preview)
    }

    private suspend fun write(
        document: ProgramJson,
        mode: ImportMode,
        scheduleMode: ScheduleMode,
    ): Long {
        val programId = resolveProgram(document, mode, scheduleMode)
        val skillIds = upsertSkills(document.skills)
        val stepIds = upsertSteps(document.skills, skillIds)
        val exerciseIds = upsertExercises(document.exercises, skillIds)
        replaceRoutine(programId, document, exerciseIds, stepIds)
        if (mode == ImportMode.REPLACE_EVERYTHING) pruneLibrary(document)
        return programId
    }

    private suspend fun resolveProgram(
        document: ProgramJson,
        mode: ImportMode,
        scheduleMode: ScheduleMode,
    ): Long {
        val programs = database.programDao().all()
        if (mode == ImportMode.ADD_PROGRAM || programs.isEmpty()) {
            val id = database.programDao().insert(
                ProgramEntity(
                    name = document.program.name,
                    scheduleMode = scheduleMode,
                    rollingSequence = 0,
                    deloadActive = false,
                    deloadStartedOn = null,
                    isActive = false,
                ),
            )
            if (programs.none { it.isActive }) activate(id)
            return id
        }
        val target = programs.firstOrNull { it.isActive } ?: programs.first()
        database.programDao().update(
            target.copy(name = document.program.name, scheduleMode = scheduleMode),
        )
        if (!target.isActive) activate(target.id)
        return target.id
    }

    private suspend fun activate(id: Long) {
        database.programDao().clearActive()
        database.programDao().markActive(id)
    }

    private suspend fun targetProgramId(mode: ImportMode): Long? {
        val programs = database.programDao().all()
        if (mode == ImportMode.ADD_PROGRAM || programs.isEmpty()) return null
        return (programs.firstOrNull { it.isActive } ?: programs.first()).id
    }

    private suspend fun upsertSkills(skills: List<ProgramJsonSkill>): Map<String, Long> {
        val existing = database.skillDao().all().associateBy { libraryKey(it.name) }
        val ids = mutableMapOf<String, Long>()
        skills.forEach { skill ->
            val match = existing[libraryKey(skill.name)]
            val id = if (match == null) {
                database.skillDao().insert(SkillEntity(name = skill.name.trim(), archivedAt = null))
            } else {
                database.skillDao().update(match.copy(name = skill.name.trim(), archivedAt = null))
                match.id
            }
            ids[skill.key] = id
        }
        return ids
    }

    private suspend fun upsertSteps(
        skills: List<ProgramJsonSkill>,
        skillIds: Map<String, Long>,
    ): Map<String, Long> {
        val ids = mutableMapOf<String, Long>()
        skills.forEach { skill ->
            val skillId = skillIds.getValue(skill.key)
            val existing = database.skillDao().getSteps(skillId).associateBy { libraryKey(it.name) }
            skill.steps.forEach { step ->
                val match = existing[libraryKey(step.name)]
                val id = if (match == null) {
                    database.skillDao().insertStep(
                        SkillStepEntity(
                            skillId = skillId,
                            sortOrder = step.order,
                            name = step.name.trim(),
                            stage1TotalSec = step.stage1TotalSec,
                            stage2TotalLowSec = step.stage2TotalLowSec,
                            stage2TotalHighSec = step.stage2TotalHighSec,
                            stage3UnbrokenSec = step.stage3UnbrokenSec,
                        ),
                    )
                } else {
                    database.skillDao().updateStep(
                        match.copy(
                            name = step.name.trim(),
                            sortOrder = step.order,
                            stage1TotalSec = step.stage1TotalSec,
                            stage2TotalLowSec = step.stage2TotalLowSec,
                            stage2TotalHighSec = step.stage2TotalHighSec,
                            stage3UnbrokenSec = step.stage3UnbrokenSec,
                        ),
                    )
                    match.id
                }
                ids[step.key] = id
            }
        }
        return ids
    }

    private suspend fun upsertExercises(
        exercises: List<ProgramJsonExercise>,
        skillIds: Map<String, Long>,
    ): Map<String, Long> {
        val existing = database.exerciseDao().all().associateBy { libraryKey(it.name) }
        val ids = mutableMapOf<String, Long>()
        exercises.forEach { exercise ->
            val match = existing[libraryKey(exercise.name)]
            val stored = ExerciseEntity(
                id = match?.id ?: 0,
                name = exercise.name.trim(),
                equipment = Equipment.valueOf(exercise.equipment),
                barWeightKg = exercise.barWeightKg,
                loadIncrementKg = exercise.loadIncrementKg,
                isUnilateral = exercise.isUnilateral,
                skillId = exercise.skillKey?.let { skillIds.getValue(it) },
                primaryMuscleGroups = exercise.primaryMuscleGroups,
                secondaryMuscleGroups = exercise.secondaryMuscleGroups,
                setupNotes = exercise.setupNotes.orEmpty(),
                archivedAt = null,
            )
            val id = if (match == null) {
                database.exerciseDao().insert(stored)
            } else {
                database.exerciseDao().update(stored)
                match.id
            }
            ids[exercise.key] = id
        }
        return ids
    }

    private suspend fun replaceRoutine(
        programId: Long,
        document: ProgramJson,
        exerciseIds: Map<String, Long>,
        stepIds: Map<String, Long>,
    ) {
        database.routineDao().days(programId).forEach { day ->
            database.routineDao().deleteDay(day.id)
        }
        document.days.forEach { day ->
            val dayId = database.routineDao().insertDay(
                RoutineDayEntity(
                    programId = programId,
                    label = day.label,
                    dayOfWeek = day.dayOfWeek?.let { DayOfWeek.valueOf(it).value },
                    sequenceIndex = day.sequenceIndex,
                    isRest = day.isRest,
                    prepDurationMin = day.prep?.durationMin,
                    cooldownDurationMin = day.cooldown?.durationMin,
                ),
            )
            insertChecklist(dayId, ChecklistPhase.PREP, day.prep)
            insertChecklist(dayId, ChecklistPhase.COOLDOWN, day.cooldown)
            day.slots.forEach { slot ->
                val slotId = database.routineDao().insertSlot(
                    RoutineSlotEntity(
                        dayId = dayId,
                        exerciseId = exerciseIds.getValue(slot.exerciseKey),
                        category = SlotCategory.valueOf(slot.category),
                        sortOrder = slot.order,
                        supersetGroup = slot.supersetGroup?.let { letter ->
                            supersetIndex(letter) ?: error("superset $letter is not a letter")
                        },
                        metricType = MetricType.valueOf(slot.metricType),
                        setsMin = slot.setsMin,
                        setsMax = slot.setsMax,
                        repsLow = slot.repsLow,
                        repsHigh = slot.repsHigh,
                        isAmrap = slot.isAmrap,
                        holdTargetSec = slot.holdTargetSec,
                        blockDurationSec = slot.blockDurationSec,
                        restMinSec = slot.restMinSec,
                        restMaxSec = slot.restMaxSec,
                        restAsNeeded = slot.restAsNeeded,
                        isOptional = slot.isOptional,
                        skipReasonLabel = slot.skipReasonLabel,
                        targetSkillStepId = slot.targetSkillStepKey?.let { stepIds.getValue(it) },
                        progressionRule = ProgressionRule.valueOf(slot.progressionRule),
                        incrementOverrideKg = slot.incrementOverrideKg,
                        notes = slot.notes,
                        holdTargetMaxSec = slot.holdTargetMaxSec,
                    ),
                )
                slot.alternativeExerciseKeys.forEach { key ->
                    database.routineDao().insertAlternative(
                        SlotAlternativeEntity(
                            slotId = slotId,
                            exerciseId = exerciseIds.getValue(key),
                        ),
                    )
                }
            }
            val cardio = day.cardio.firstOrNull() ?: return@forEach
            database.routineDao().upsertCardioPlan(
                CardioPlanEntity(
                    dayId = dayId,
                    type = CardioType.valueOf(cardio.type),
                    targetDistanceM = cardio.targetDistanceM,
                    isOptional = cardio.isOptional,
                    label = cardio.label,
                ),
            )
        }
    }

    private suspend fun insertChecklist(
        dayId: Long,
        phase: ChecklistPhase,
        checklist: ProgramJsonChecklist?,
    ) {
        checklist?.items?.forEach { item ->
            database.routineDao().insertChecklist(
                ChecklistItemEntity(
                    dayId = dayId,
                    phase = phase,
                    text = item.text,
                    reps = item.reps,
                    seconds = item.seconds,
                ),
            )
        }
    }

    private suspend fun pruneLibrary(document: ProgramJson) {
        val keepExercises = document.exercises.map { libraryKey(it.name) }.toSet()
        val referenced = referencedExerciseIds(ignoringProgramId = null)
        database.exerciseDao().all().forEach { exercise ->
            if (libraryKey(exercise.name) in keepExercises) return@forEach
            if (exercise.id in referenced) {
                database.exerciseDao().archive(exercise.id, clock.instant())
            } else {
                deleteOrArchiveExercise(exercise.id)
            }
        }
        val keepSkills = document.skills.map { libraryKey(it.name) }.toSet()
        val skillsInUse = database.exerciseDao().all().mapNotNull { it.skillId }.toSet()
        val targetedSteps = targetedStepIds()
        database.skillDao().all().forEach { skill ->
            if (libraryKey(skill.name) in keepSkills) return@forEach
            val steps = database.skillDao().getSteps(skill.id)
            val inUse = skill.id in skillsInUse ||
                database.skillDao().getProgress(skill.id) != null ||
                steps.any { it.id in targetedSteps }
            if (inUse) {
                database.skillDao().archive(skill.id, clock.instant())
            } else {
                deleteOrArchiveSkill(skill.id)
            }
        }
    }

    private suspend fun deleteOrArchiveExercise(id: Long) {
        try {
            database.exerciseDao().delete(id)
        } catch (error: SQLiteConstraintException) {
            database.exerciseDao().archive(id, clock.instant())
        }
    }

    private suspend fun deleteOrArchiveSkill(id: Long) {
        try {
            database.skillDao().delete(id)
        } catch (error: SQLiteConstraintException) {
            database.skillDao().archive(id, clock.instant())
        }
    }

    private suspend fun referencedExerciseIds(ignoringProgramId: Long?): Set<Long> {
        val ignoredDays = if (ignoringProgramId == null) {
            emptySet()
        } else {
            database.routineDao().days(ignoringProgramId).map { it.id }.toSet()
        }
        val slots = database.routineDao().allSlots().filter { it.dayId !in ignoredDays }
        val slotIds = slots.map { it.id }.toSet()
        val ids = mutableSetOf<Long>()
        ids += slots.map { it.exerciseId }
        ids += database.routineDao().allAlternatives()
            .filter { it.slotId in slotIds }
            .map { it.exerciseId }
        database.sessionDao().allSlots().forEach { slot ->
            ids += slot.prescriptionSnapshot.exerciseId
            slot.chosenAlternativeExerciseId?.let { ids += it }
        }
        ids += database.mediaDao().referencedExerciseIds()
        return ids
    }

    private suspend fun targetedStepIds(): Set<Long> {
        val ids = database.routineDao().targetedStepIds().toMutableSet()
        database.sessionDao().allSlots().forEach { slot ->
            slot.prescriptionSnapshot.targetSkillStepId?.let { ids += it }
        }
        return ids
    }
}
