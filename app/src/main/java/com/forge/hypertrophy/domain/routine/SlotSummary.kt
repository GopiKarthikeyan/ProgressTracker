package com.forge.hypertrophy.domain.routine

import com.forge.hypertrophy.domain.model.MetricType

/** Compact prescription line for Day editor slot rows (e.g. `3×8–12`, `3×AMRAP`, `3×30s`). */
fun slotPrescriptionSummary(
    setsMin: Int,
    setsMax: Int,
    repsLow: Int?,
    repsHigh: Int?,
    isAmrap: Boolean,
    metricType: MetricType,
    holdTargetSec: Int?,
): String {
    val sets = if (setsMin == setsMax) setsMin.toString() else "$setsMin–$setsMax"
    val work = when {
        metricType == MetricType.HOLD || metricType == MetricType.TIMED_BLOCK -> {
            val sec = holdTargetSec
            if (sec != null) "${sec}s" else "hold"
        }
        isAmrap -> "AMRAP"
        repsLow != null && repsHigh != null && repsLow != repsHigh -> "$repsLow–$repsHigh"
        repsLow != null -> repsLow.toString()
        repsHigh != null -> repsHigh.toString()
        else -> "—"
    }
    return "$sets×$work"
}

fun slotRowDetailLine(
    prescription: String,
    baselineWeightKg: Double?,
): String = if (baselineWeightKg != null) {
    val kg = if (baselineWeightKg % 1.0 == 0.0) {
        baselineWeightKg.toLong().toString()
    } else {
        baselineWeightKg.toString()
    }
    "$prescription · $kg kg"
} else {
    prescription
}
