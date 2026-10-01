package com.forge.hypertrophy.domain.model

/**
 * One logged set. [weightKg] is the bar plus plates for loaded equipment.
 * For [Equipment.WEIGHTED_BODYWEIGHT] it is added load only, never bodyweight
 * plus that added load. [Equipment.BODYWEIGHT] rep sets leave it null.
 */
data class LoggedSet(
    val weightKg: Double?,
    val reps: Int?,
    val setType: SetType,
)

/**
 * Sets from a single session for one slot. Most-recent-first lists use this.
 * A [calibration] session established the slot's starting weight and is left
 * out of progression.
 */
data class SlotSession(
    val sets: List<LoggedSet>,
    val calibration: Boolean = false,
)

enum class ProgressionAction {
    HOLD,
    INCREASE,
    DECREASE,
    VARIATION_OR_ADDED_LOAD,
    STALL,
    COLD_START,
    /** A stored starting weight, used only when the slot itself has no history. */
    BASELINE,
}

data class ProgressionSuggestion(
    val action: ProgressionAction,
    val weightKg: Double?,
)

data class ProgressionInput(
    val rule: ProgressionRule,
    val equipment: Equipment,
    val metricType: MetricType,
    val repsLow: Int,
    val repsHigh: Int,
    val exerciseIncrementKg: Double,
    val incrementOverrideKg: Double?,
    /** Most recent session first. */
    val slotSessions: List<SlotSession>,
    /** Latest working weight for this exercise from any slot, including other slots. */
    val latestWeightFromAnySlotKg: Double?,
    /**
     * Starting weight for this slot. Used when the slot has no non-calibration
     * history, and it wins over [latestWeightFromAnySlotKg].
     */
    val baselineWeightKg: Double? = null,
    /**
     * The lifter chose "calibrate in the first session" and that session has
     * not been logged yet. No weight is suggested, including the cold start.
     */
    val awaitingCalibration: Boolean = false,
)
