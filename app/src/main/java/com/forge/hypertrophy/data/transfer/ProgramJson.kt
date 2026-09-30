package com.forge.hypertrophy.data.transfer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val ProgramJsonFormat: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
    prettyPrint = true
}

@Serializable
data class ProgramJson(
    val schemaVersion: Int,
    val program: ProgramJsonHeader,
    val defaults: ProgramJsonDefaults,
    val skills: List<ProgramJsonSkill> = emptyList(),
    val exercises: List<ProgramJsonExercise> = emptyList(),
    val days: List<ProgramJsonDay> = emptyList(),
)

@Serializable
data class ProgramJsonHeader(
    val name: String,
    val sourceVersion: String = "v1",
)

@Serializable
data class ProgramJsonDefaults(
    val transitionRestSec: Int,
    val barWeightKg: Double,
    val plateInventoryKg: List<Double> = emptyList(),
)

@Serializable
data class ProgramJsonSkill(
    val key: String,
    val name: String,
    val initialStepKey: String,
    val steps: List<ProgramJsonSkillStep> = emptyList(),
)

@Serializable
data class ProgramJsonSkillStep(
    val key: String,
    val name: String,
    val order: Int,
    val stage1TotalSec: Int = 12,
    @SerialName("stage2TotalMinSec") val stage2TotalLowSec: Int = 15,
    @SerialName("stage2TotalMaxSec") val stage2TotalHighSec: Int = 18,
    @SerialName("masteryUnbrokenSec") val stage3UnbrokenSec: Int = 10,
)

@Serializable
data class ProgramJsonExercise(
    val key: String,
    val name: String,
    val equipment: String,
    val barWeightKg: Double? = null,
    val loadIncrementKg: Double = 0.0,
    val isUnilateral: Boolean = false,
    val skillKey: String? = null,
    @SerialName("primaryMuscles") val primaryMuscleGroups: List<String> = emptyList(),
    @SerialName("secondaryMuscles") val secondaryMuscleGroups: List<String> = emptyList(),
    val setupNotes: String? = null,
)

@Serializable
data class ProgramJsonDay(
    val key: String,
    val label: String,
    val dayOfWeek: String? = null,
    val sequenceIndex: Int,
    val isRest: Boolean = false,
    val prep: ProgramJsonChecklist? = null,
    val cooldown: ProgramJsonChecklist? = null,
    val slots: List<ProgramJsonSlot> = emptyList(),
    val cardio: List<ProgramJsonCardio> = emptyList(),
)

@Serializable
data class ProgramJsonChecklist(
    val durationMin: Int? = null,
    val items: List<ProgramJsonChecklistItem> = emptyList(),
)

@Serializable
data class ProgramJsonChecklistItem(
    val text: String,
    val reps: Int? = null,
    val seconds: Int? = null,
)

@Serializable
data class ProgramJsonSlot(
    val key: String,
    val exerciseKey: String,
    val category: String,
    val metricType: String,
    val setsMin: Int,
    val setsMax: Int,
    val repsLow: Int? = null,
    val repsHigh: Int? = null,
    val isAmrap: Boolean = false,
    val holdTargetSec: Int? = null,
    val holdTargetMaxSec: Int? = null,
    val blockDurationSec: Int? = null,
    val restMinSec: Int? = null,
    val restMaxSec: Int? = null,
    val restAsNeeded: Boolean = false,
    val isOptional: Boolean = false,
    val skipReasonLabel: String? = null,
    val supersetGroup: String? = null,
    val targetSkillStepKey: String? = null,
    val progressionRule: String,
    val incrementOverrideKg: Double? = null,
    val alternativeExerciseKeys: List<String> = emptyList(),
    val notes: String? = null,
    val order: Int,
)

@Serializable
data class ProgramJsonCardio(
    val type: String,
    val label: String = "",
    val targetDistanceM: Int? = null,
    val isOptional: Boolean = false,
)

internal fun libraryKey(name: String): String = name.trim().lowercase()

/** JSON letters A–Z map to stored groups 1–26. */
internal fun supersetIndex(letter: String): Int? {
    val trimmed = letter.trim()
    if (trimmed.length != 1 || !trimmed[0].isLetter()) return null
    return trimmed.uppercase()[0] - 'A' + 1
}

internal fun supersetLetter(group: Int?): String? {
    if (group == null || group !in 1..26) return null
    return ('A' + group - 1).toString()
}
