package com.forge.hypertrophy.domain.dayplan

import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.SlotPrescription

/**
 * Compact prescription readout for Day Plan rows.
 * Pure Kotlin — no Android resources.
 */
object SchemeSummaryFormatter {
    fun format(prescription: SlotPrescription): String {
        return when (prescription.metricType) {
            MetricType.TIMED_BLOCK -> {
                val sec = prescription.blockDurationSec ?: 0
                val mins = (sec + 59) / 60
                if (mins > 0) "$mins Mins" else "${sec}s"
            }
            MetricType.HOLD, MetricType.HOLD_OR_REPS -> {
                val sets = setRange(prescription)
                "Hold".let { hold ->
                    if (sets != null) "$sets x $hold" else hold
                }
            }
            MetricType.REPS, MetricType.WEIGHT_REPS -> {
                if (isRepLadder(prescription)) {
                    repLadder(prescription)
                } else {
                    val sets = setRange(prescription) ?: return repRange(prescription) ?: ""
                    val reps = repRange(prescription) ?: return sets
                    "$sets x $reps"
                }
            }
        }
    }

    fun formatChecklist(reps: Int?, seconds: Int?): String {
        return when {
            seconds != null && seconds > 0 -> {
                val mins = (seconds + 59) / 60
                if (seconds >= 60) "$mins Mins" else "${seconds}s"
            }
            reps != null && reps > 0 -> "$reps Reps"
            else -> ""
        }
    }

    /**
     * Descending (or ascending) ladders store the first-set reps in [SlotPrescription.repsLow]
     * and the last-set reps in [SlotPrescription.repsHigh] with low > high (classic drop ladder).
     * A normal target range keeps low <= high (e.g. 6–8).
     */
    private fun isRepLadder(prescription: SlotPrescription): Boolean {
        val low = prescription.repsLow ?: return false
        val high = prescription.repsHigh ?: return false
        return prescription.setsMax > 1 &&
            prescription.setsMin == prescription.setsMax &&
            low > high
    }

    private fun repLadder(prescription: SlotPrescription): String {
        val first = prescription.repsLow ?: return ""
        val last = prescription.repsHigh ?: return ""
        val sets = prescription.setsMax.coerceAtLeast(1)
        if (sets == 1) return "$first Reps"
        val step = (last - first).toDouble() / (sets - 1)
        return (0 until sets).joinToString(" • ") { i ->
            val reps = (first + step * i).toInt()
            "$reps Reps"
        }
    }

    private fun setRange(prescription: SlotPrescription): String? {
        val min = prescription.setsMin
        val max = prescription.setsMax
        if (min <= 0 && max <= 0) return null
        return if (min == max) "$max" else "$min-$max"
    }

    private fun repRange(prescription: SlotPrescription): String? {
        val low = prescription.repsLow
        val high = prescription.repsHigh
        return when {
            low == null && high == null -> null
            low != null && high != null && low == high -> low.toString()
            low != null && high != null -> "$low-$high"
            low != null -> low.toString()
            else -> high!!.toString()
        }
    }
}
