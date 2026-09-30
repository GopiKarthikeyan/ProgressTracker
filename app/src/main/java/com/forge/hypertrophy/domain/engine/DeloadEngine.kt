package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.SlotSession
import java.math.BigDecimal
import java.math.RoundingMode

data class DeloadBaseline(
    val equipment: Equipment,
    val metricType: MetricType,
    /** Bar plus plates, or added load when [equipment] is [Equipment.WEIGHTED_BODYWEIGHT]. */
    val weightKg: Double?,
    val holdSec: Int?,
    val incrementKg: Double,
)

data class DeloadPrescription(
    val weightKg: Double?,
    val holdSec: Int?,
    val progression: ProgressionAction,
)

data class DeloadHistoryEntry(
    val duringDeload: Boolean,
    val weightKg: Double?,
    val holdSec: Int?,
)

/** One slot session, most-recent-first lists, flagged when it was logged in the deload. */
data class TaggedSlotSession(
    val session: SlotSession,
    val duringDeload: Boolean,
)

/**
 * One rotation of reduced work, then the previous loads come back.
 *
 * Loaded weight is cut by 20% and rounded with [LoadRounding.roundToIncrement].
 * [Equipment.WEIGHTED_BODYWEIGHT] cuts added load only; bodyweight is not an
 * input. [MetricType.HOLD] cuts the total hold by 50% and rounds half-up to a
 * whole second. While the deload is active, [DeloadPrescription.progression]
 * is [ProgressionAction.HOLD].
 *
 * [postDeloadSessions] keeps only the sessions after the deload block. Passing
 * that list to [DoubleProgressionEngine] is what stops the pre-deload stall
 * from firing again the moment the deload ends.
 *
 * [lastNonDeload] returns null when every recorded session is itself a deload,
 * or when there is no history. The caller refuses to start the deload in that
 * case. There is no pre-deload weight to restore, and the current prescription
 * is not used as a stand-in baseline.
 */
class DeloadEngine {
    fun prescribe(baseline: DeloadBaseline): DeloadPrescription {
        val weighted = baseline.equipment == Equipment.WEIGHTED_BODYWEIGHT
        val staticHold = baseline.metricType == MetricType.HOLD && !weighted
        return if (staticHold) {
            DeloadPrescription(
                weightKg = null,
                holdSec = baseline.holdSec?.let(::cutHold),
                progression = ProgressionAction.HOLD,
            )
        } else {
            DeloadPrescription(
                weightKg = baseline.weightKg?.let { cutLoad(it, baseline.incrementKg) },
                holdSec = null,
                progression = ProgressionAction.HOLD,
            )
        }
    }

    fun restoredWeightKg(baselineWeightKg: Double): Double {
        return LoadRounding.roundToDecimals(baselineWeightKg)
    }

    fun rotationFinished(completedTrainingDays: Int, daysInRotation: Int): Boolean {
        if (daysInRotation <= 0) return false
        return completedTrainingDays >= daysInRotation
    }

    /**
     * Most recent first. The first entry that was not logged during the deload.
     * Null means there is no baseline: the caller refuses to start the deload
     * rather than cutting the current prescription.
     */
    fun lastNonDeload(historyMostRecentFirst: List<DeloadHistoryEntry>): DeloadHistoryEntry? {
        return historyMostRecentFirst.firstOrNull { !it.duringDeload }
    }

    /**
     * Most recent first. Keeps the sessions logged after the deload and stops
     * at the deload block, so pre-deload sessions are not returned.
     */
    fun postDeloadSessions(historyMostRecentFirst: List<TaggedSlotSession>): List<SlotSession> {
        return historyMostRecentFirst.takeWhile { !it.duringDeload }.map { it.session }
    }

    private fun cutLoad(weightKg: Double, incrementKg: Double): Double {
        val reduced = BigDecimal.valueOf(weightKg).multiply(BigDecimal("0.80")).toDouble()
        return LoadRounding.roundToIncrement(reduced, incrementKg)
    }

    private fun cutHold(holdSec: Int): Int {
        return BigDecimal.valueOf(holdSec.toLong())
            .multiply(BigDecimal("0.50"))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }
}
