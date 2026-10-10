package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.ScheduleMode
import javax.inject.Inject

data class LibraryCatalogResult(
    val addedExercises: Int,
    val addedSkills: Int,
)

/**
 * Inserts catalog rows whose trimmed, case-insensitive name is not already
 * present. Archived rows still count as present. Existing rows are never
 * updated, un-archived, or deleted. Programs and routines are untouched.
 */
interface LibraryCatalogImporter {
    suspend fun import(document: ProgramJson): LibraryCatalogResult
}

class RoomLibraryCatalogImporter @Inject constructor(
    private val database: AppDatabase,
) : LibraryCatalogImporter {
    override suspend fun import(document: ProgramJson): LibraryCatalogResult {
        val report = ProgramJsonValidator.validate(document, ScheduleMode.FIXED)
        if (report.errors.isNotEmpty()) {
            return LibraryCatalogResult(addedExercises = 0, addedSkills = 0)
        }
        return database.runTransaction { write(document) }
    }

    private suspend fun write(document: ProgramJson): LibraryCatalogResult {
        val skillIds = mutableMapOf<String, Long>()
        var addedSkills = 0
        val existingSkills = database.skillDao().all().associateBy { libraryKey(it.name) }
        document.skills.forEach { skill ->
            val match = existingSkills[libraryKey(skill.name)]
            if (match != null) {
                skillIds[skill.key] = match.id
                return@forEach
            }
            val id = database.skillDao().insert(
                SkillEntity(name = skill.name.trim(), archivedAt = null),
            )
            skillIds[skill.key] = id
            addedSkills += 1
            skill.steps.forEach { step ->
                database.skillDao().insertStep(
                    SkillStepEntity(
                        skillId = id,
                        sortOrder = step.order,
                        name = step.name.trim(),
                        stage1TotalSec = step.stage1TotalSec,
                        stage2TotalLowSec = step.stage2TotalLowSec,
                        stage2TotalHighSec = step.stage2TotalHighSec,
                        stage3UnbrokenSec = step.stage3UnbrokenSec,
                    ),
                )
            }
        }

        var addedExercises = 0
        val existingExercises = database.exerciseDao().all().associateBy { libraryKey(it.name) }
        document.exercises.forEach { exercise ->
            if (existingExercises.containsKey(libraryKey(exercise.name))) return@forEach
            database.exerciseDao().insert(
                ExerciseEntity(
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
                ),
            )
            addedExercises += 1
        }
        return LibraryCatalogResult(addedExercises = addedExercises, addedSkills = addedSkills)
    }
}
