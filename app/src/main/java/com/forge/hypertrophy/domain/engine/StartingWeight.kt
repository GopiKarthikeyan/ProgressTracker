package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.SlotSession
import java.time.Instant

/**
 * A lifter-supplied starting load for one slot. [weightKg] null means the
 * first session calibrates it: that session shows no suggestion, and its
 * logged sets become this baseline.
 */
data class StartingWeight(
    val weightKg: Double?,
    val repsHint: Int?,
    val setAt: Instant,
) {
    val awaitingCalibration: Boolean get() = weightKg == null
}

/**
 * The session whose completion instant equals [StartingWeight.setAt] is the
 * one that wrote the baseline. Later sessions are normal history.
 */
fun isCalibrationSession(completedAt: Instant?, baseline: StartingWeight?): Boolean {
    if (baseline == null || baseline.weightKg == null || completedAt == null) return false
    return completedAt == baseline.setAt
}

/** Most recent session first. Calibration sessions stay in the list and are flagged. */
fun slotSessionsForProgression(
    groups: List<Pair<Instant?, List<LoggedSet>>>,
    baseline: StartingWeight?,
): List<SlotSession> = groups
    .sortedByDescending { it.first ?: Instant.EPOCH }
    .map { (completedAt, sets) ->
        SlotSession(sets, calibration = isCalibrationSession(completedAt, baseline))
    }
