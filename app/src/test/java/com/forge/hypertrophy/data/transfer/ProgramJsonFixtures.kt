package com.forge.hypertrophy.data.transfer

import java.io.File

internal fun programDocument(
    schemaVersion: Int = 1,
    name: String = "Protocol",
    exercises: List<ProgramJsonExercise> = listOf(programExercise("squat")),
    skills: List<ProgramJsonSkill> = emptyList(),
    days: List<ProgramJsonDay> = listOf(programDay()),
) = ProgramJson(
    schemaVersion = schemaVersion,
    program = ProgramJsonHeader(name, "v1"),
    defaults = ProgramJsonDefaults(
        transitionRestSec = 120,
        barWeightKg = 20.0,
        plateInventoryKg = listOf(20.0, 10.0),
    ),
    skills = skills,
    exercises = exercises,
    days = days,
)

internal fun programExercise(
    key: String,
    name: String = key,
    equipment: String = "BARBELL",
    skillKey: String? = null,
) = ProgramJsonExercise(
    key = key,
    name = name,
    equipment = equipment,
    loadIncrementKg = 2.5,
    skillKey = skillKey,
)

internal fun programSkill(
    key: String,
    stepKey: String,
    initialStepKey: String = stepKey,
    stepName: String = stepKey,
) = ProgramJsonSkill(
    key = key,
    name = key,
    initialStepKey = initialStepKey,
    steps = listOf(ProgramJsonSkillStep(key = stepKey, name = stepName, order = 0)),
)

internal fun programDay(
    key: String = "mon",
    weekday: String? = "MONDAY",
    sequence: Int = 0,
    rest: Boolean = false,
    slots: List<ProgramJsonSlot> = listOf(programSlot()),
    cardio: List<ProgramJsonCardio> = emptyList(),
    prep: ProgramJsonChecklist? = null,
    cooldown: ProgramJsonChecklist? = null,
) = ProgramJsonDay(
    key = key,
    label = key,
    dayOfWeek = weekday,
    sequenceIndex = sequence,
    isRest = rest,
    prep = prep,
    cooldown = cooldown,
    slots = slots,
    cardio = cardio,
)

internal fun programSlot(
    key: String = "s1",
    exerciseKey: String = "squat",
    setsMin: Int = 3,
    setsMax: Int = 5,
    repsLow: Int? = 6,
    repsHigh: Int? = 10,
    restMin: Int? = 60,
    restMax: Int? = 90,
    hold: Int? = null,
    holdMax: Int? = null,
    superset: String? = null,
    stepKey: String? = null,
    alternatives: List<String> = emptyList(),
    notes: String? = null,
    order: Int = 0,
) = ProgramJsonSlot(
    key = key,
    exerciseKey = exerciseKey,
    category = "COMPOUND",
    metricType = "WEIGHT_REPS",
    setsMin = setsMin,
    setsMax = setsMax,
    repsLow = repsLow,
    repsHigh = repsHigh,
    restMinSec = restMin,
    restMaxSec = restMax,
    holdTargetSec = hold,
    holdTargetMaxSec = holdMax,
    supersetGroup = superset,
    targetSkillStepKey = stepKey,
    progressionRule = "DOUBLE",
    alternativeExerciseKeys = alternatives,
    notes = notes,
    order = order,
)

internal fun repoFile(relative: String): File {
    val start = System.getProperty("user.dir") ?: error("user.dir is unset")
    var dir: File? = File(start)
    while (dir != null) {
        val candidate = File(dir, relative)
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    error("Could not find $relative from ${System.getProperty("user.dir")}")
}
