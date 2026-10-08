package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SlotCategory
import java.time.DayOfWeek

enum class ProgramJsonSeverity {
    ERROR,
    WARNING,
}

enum class ProgramJsonIssueKind(val severity: ProgramJsonSeverity) {
    UNSUPPORTED_SCHEMA_VERSION(ProgramJsonSeverity.ERROR),
    DUPLICATE_KEY(ProgramJsonSeverity.ERROR),
    DANGLING_EXERCISE(ProgramJsonSeverity.ERROR),
    DANGLING_SKILL(ProgramJsonSeverity.ERROR),
    DANGLING_SKILL_STEP(ProgramJsonSeverity.ERROR),
    DANGLING_ALTERNATIVE(ProgramJsonSeverity.ERROR),
    DANGLING_INITIAL_STEP(ProgramJsonSeverity.ERROR),
    SETS_RANGE(ProgramJsonSeverity.ERROR),
    REPS_RANGE(ProgramJsonSeverity.ERROR),
    REST_RANGE(ProgramJsonSeverity.ERROR),
    HOLD_RANGE(ProgramJsonSeverity.ERROR),
    DUPLICATE_WEEKDAY(ProgramJsonSeverity.ERROR),
    DUPLICATE_SEQUENCE(ProgramJsonSeverity.ERROR),
    UNKNOWN_ENUM(ProgramJsonSeverity.ERROR),
    MISSING_WEEKDAY(ProgramJsonSeverity.ERROR),
    MULTIPLE_CARDIO(ProgramJsonSeverity.ERROR),
    UNKNOWN_SUPERSET(ProgramJsonSeverity.ERROR),
    SUPERSET_SINGLE_MEMBER(ProgramJsonSeverity.WARNING),
    REST_DAY_HAS_SLOTS(ProgramJsonSeverity.WARNING),
    ALTERNATIVE_IS_SELF(ProgramJsonSeverity.WARNING),
}

data class ProgramJsonIssue(
    val kind: ProgramJsonIssueKind,
    val path: String,
    val message: String,
)

data class ProgramJsonReport(
    val errors: List<ProgramJsonIssue>,
    val warnings: List<ProgramJsonIssue>,
)

/**
 * Checks a parsed program document. Schedule mode is not part of the JSON;
 * weekday rules apply only when the caller passes [ScheduleMode.FIXED].
 */
object ProgramJsonValidator {
    fun validate(
        document: ProgramJson,
        scheduleMode: ScheduleMode = ScheduleMode.FIXED,
    ): ProgramJsonReport {
        val issues = mutableListOf<ProgramJsonIssue>()
        if (document.schemaVersion != 1) {
            issues += issue(
                ProgramJsonIssueKind.UNSUPPORTED_SCHEMA_VERSION,
                "schemaVersion",
                "schemaVersion ${document.schemaVersion} is not supported",
            )
        }
        duplicateKeys(issues, "exercises", document.exercises.map { it.key })
        duplicateKeys(issues, "skills", document.skills.map { it.key })
        duplicateKeys(issues, "days", document.days.map { it.key })
        duplicateKeys(issues, "steps", document.skills.flatMap { skill -> skill.steps.map { it.key } })
        duplicateKeys(issues, "slots", document.days.flatMap { day -> day.slots.map { it.key } })

        val exerciseKeys = document.exercises.map { it.key }.toSet()
        val skillKeys = document.skills.map { it.key }.toSet()
        val stepKeys = document.skills.flatMap { skill -> skill.steps.map { it.key } }.toSet()

        document.skills.forEachIndexed { index, skill ->
            val ownSteps = skill.steps.map { it.key }.toSet()
            if (skill.initialStepKey !in ownSteps) {
                issues += issue(
                    ProgramJsonIssueKind.DANGLING_INITIAL_STEP,
                    "skills[$index].initialStepKey",
                    "initial step ${skill.initialStepKey} is not a step of ${skill.key}",
                )
            }
        }
        document.exercises.forEachIndexed { index, exercise ->
            requireEnum<Equipment>(issues, exercise.equipment, "exercises[$index].equipment")
            val skillKey = exercise.skillKey
            if (skillKey != null && skillKey !in skillKeys) {
                issues += issue(
                    ProgramJsonIssueKind.DANGLING_SKILL,
                    "exercises[$index].skillKey",
                    "skill $skillKey does not exist",
                )
            }
        }

        val weekdays = mutableSetOf<String>()
        val sequences = mutableSetOf<Int>()
        document.days.forEachIndexed { dayIndex, day ->
            val weekday = day.dayOfWeek
            if (weekday == null) {
                if (scheduleMode == ScheduleMode.FIXED) {
                    issues += issue(
                        ProgramJsonIssueKind.MISSING_WEEKDAY,
                        "days[$dayIndex].dayOfWeek",
                        "a fixed program needs a weekday",
                    )
                }
            } else if (DayOfWeek.entries.none { it.name == weekday }) {
                issues += issue(
                    ProgramJsonIssueKind.UNKNOWN_ENUM,
                    "days[$dayIndex].dayOfWeek",
                    "$weekday is not a weekday",
                )
            } else if (scheduleMode == ScheduleMode.FIXED && !weekdays.add(weekday)) {
                issues += issue(
                    ProgramJsonIssueKind.DUPLICATE_WEEKDAY,
                    "days[$dayIndex].dayOfWeek",
                    "$weekday is already used",
                )
            }
            if (!sequences.add(day.sequenceIndex)) {
                issues += issue(
                    ProgramJsonIssueKind.DUPLICATE_SEQUENCE,
                    "days[$dayIndex].sequenceIndex",
                    "sequence ${day.sequenceIndex} is already used",
                )
            }
            if (day.isRest && day.slots.isNotEmpty()) {
                issues += issue(
                    ProgramJsonIssueKind.REST_DAY_HAS_SLOTS,
                    "days[$dayIndex]",
                    "a rest day has slots",
                )
            }
            if (day.cardio.size > 1) {
                issues += issue(
                    ProgramJsonIssueKind.MULTIPLE_CARDIO,
                    "days[$dayIndex].cardio",
                    "a day stores one cardio plan",
                )
            }
            day.cardio.forEachIndexed { cardioIndex, cardio ->
                requireEnum<CardioType>(issues, cardio.type, "days[$dayIndex].cardio[$cardioIndex].type")
            }
            val groupCounts = mutableMapOf<String, Int>()
            day.slots.forEachIndexed { slotIndex, slot ->
                val path = "days[$dayIndex].slots[$slotIndex]"
                if (slot.exerciseKey !in exerciseKeys) {
                    issues += issue(
                        ProgramJsonIssueKind.DANGLING_EXERCISE,
                        "$path.exerciseKey",
                        "exercise ${slot.exerciseKey} does not exist",
                    )
                }
                requireEnum<SlotCategory>(issues, slot.category, "$path.category")
                requireEnum<MetricType>(issues, slot.metricType, "$path.metricType")
                requireEnum<ProgressionRule>(issues, slot.progressionRule, "$path.progressionRule")
                if (slot.setsMin > slot.setsMax) {
                    issues += issue(ProgramJsonIssueKind.SETS_RANGE, path, "setsMin is above setsMax")
                }
                val repsLow = slot.repsLow
                val repsHigh = slot.repsHigh
                if (repsLow != null && repsHigh != null && repsLow > repsHigh) {
                    issues += issue(ProgramJsonIssueKind.REPS_RANGE, path, "repsLow is above repsHigh")
                }
                val restMin = slot.restMinSec
                val restMax = slot.restMaxSec
                if (restMin != null && restMax != null && restMin > restMax) {
                    issues += issue(ProgramJsonIssueKind.REST_RANGE, path, "restMinSec is above restMaxSec")
                }
                val hold = slot.holdTargetSec
                val holdMax = slot.holdTargetMaxSec
                if (hold != null && holdMax != null && hold > holdMax) {
                    issues += issue(ProgramJsonIssueKind.HOLD_RANGE, path, "holdTargetSec is above holdTargetMaxSec")
                }
                val stepKey = slot.targetSkillStepKey
                if (stepKey != null && stepKey !in stepKeys) {
                    issues += issue(
                        ProgramJsonIssueKind.DANGLING_SKILL_STEP,
                        "$path.targetSkillStepKey",
                        "skill step $stepKey does not exist",
                    )
                }
                val rawGroup = slot.supersetGroup
                val group = rawGroup?.let(::supersetIndex)
                if (!rawGroup.isNullOrBlank() && group == null) {
                    issues += issue(
                        ProgramJsonIssueKind.UNKNOWN_SUPERSET,
                        "$path.supersetGroup",
                        "$rawGroup is not a single letter",
                    )
                }
                if (group != null) {
                    supersetLetter(group)?.let { letter ->
                        groupCounts[letter] = (groupCounts[letter] ?: 0) + 1
                    }
                }
                slot.alternativeExerciseKeys.forEachIndexed { altIndex, alternative ->
                    if (alternative == slot.exerciseKey) {
                        issues += issue(
                            ProgramJsonIssueKind.ALTERNATIVE_IS_SELF,
                            "$path.alternativeExerciseKeys[$altIndex]",
                            "an alternative matches the slot exercise",
                        )
                    } else if (alternative !in exerciseKeys) {
                        issues += issue(
                            ProgramJsonIssueKind.DANGLING_ALTERNATIVE,
                            "$path.alternativeExerciseKeys[$altIndex]",
                            "alternative $alternative does not exist",
                        )
                    }
                }
            }
            groupCounts.filterValues { it == 1 }.keys.sorted().forEach { letter ->
                issues += issue(
                    ProgramJsonIssueKind.SUPERSET_SINGLE_MEMBER,
                    "days[$dayIndex].supersetGroup",
                    "superset $letter has one member",
                )
            }
        }

        return ProgramJsonReport(
            errors = issues.filter { it.kind.severity == ProgramJsonSeverity.ERROR },
            warnings = issues.filter { it.kind.severity == ProgramJsonSeverity.WARNING },
        )
    }

    private fun duplicateKeys(issues: MutableList<ProgramJsonIssue>, label: String, keys: List<String>) {
        val seen = mutableSetOf<String>()
        keys.forEachIndexed { index, key ->
            if (!seen.add(key)) {
                issues += issue(
                    ProgramJsonIssueKind.DUPLICATE_KEY,
                    "$label[$index].key",
                    "duplicate key $key",
                )
            }
        }
    }

    private inline fun <reified T : Enum<T>> requireEnum(
        issues: MutableList<ProgramJsonIssue>,
        value: String,
        path: String,
    ) {
        if (enumValues<T>().none { it.name == value }) {
            issues += issue(ProgramJsonIssueKind.UNKNOWN_ENUM, path, "$value is not a known name")
        }
    }

    private fun issue(kind: ProgramJsonIssueKind, path: String, message: String) =
        ProgramJsonIssue(kind, path, message)
}
