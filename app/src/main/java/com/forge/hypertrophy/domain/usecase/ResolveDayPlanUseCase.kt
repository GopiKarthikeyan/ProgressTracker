package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.engine.SessionEstimator
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.TrainingDay
import java.time.Clock
import java.time.LocalDate

enum class DayPlanCommitment {
    /** FIXED mapping or ROLLING assignment already known / today. */
    COMMITTED,
    /** ROLLING future walk from the current pointer — may shift. */
    PROJECTED,
    /** No day resolved for this date. */
    UNRESOLVED,
}

data class ResolvedDayPlan(
    val date: LocalDate,
    val day: TrainingDay?,
    val commitment: DayPlanCommitment,
    val estimatedSeconds: Int,
)

/**
 * Resolves the training day shown on the Day Plan preview for any calendar
 * date. FIXED maps by weekday (and swaps). ROLLING uses committed mappings /
 * today's pointer for known dates and projects the rotation forward for
 * future dates past the last committed day.
 */
class ResolveDayPlanUseCase(
    private val clock: Clock,
    private val estimator: SessionEstimator = SessionEstimator(),
) {
    fun resolve(
        snapshot: ScheduleSnapshot,
        date: LocalDate,
        transitionRestSeconds: Int,
    ): ResolvedDayPlan {
        val today = clock.localDate()
        return when (snapshot.mode) {
            ScheduleMode.FIXED -> {
                val day = snapshot.dayOn(date, today)
                ResolvedDayPlan(
                    date = date,
                    day = day,
                    commitment = if (day != null) DayPlanCommitment.COMMITTED else DayPlanCommitment.UNRESOLVED,
                    estimatedSeconds = day?.let { estimator.estimate(it.slots, transitionRestSeconds) } ?: 0,
                )
            }
            ScheduleMode.ROLLING -> resolveRolling(snapshot, date, today, transitionRestSeconds)
        }
    }

    private fun resolveRolling(
        snapshot: ScheduleSnapshot,
        date: LocalDate,
        today: LocalDate,
        transitionRestSeconds: Int,
    ): ResolvedDayPlan {
        val committed = snapshot.dayOn(date, today)
        if (committed != null) {
            return ResolvedDayPlan(
                date = date,
                day = committed,
                commitment = DayPlanCommitment.COMMITTED,
                estimatedSeconds = estimator.estimate(committed.slots, transitionRestSeconds),
            )
        }
        if (date.isAfter(today)) {
            val projected = projectRolling(snapshot, date, today) ?: return unresolved(date)
            return ResolvedDayPlan(
                date = date,
                day = projected,
                commitment = DayPlanCommitment.PROJECTED,
                estimatedSeconds = estimator.estimate(projected.slots, transitionRestSeconds),
            )
        }
        return unresolved(date)
    }

    /**
     * Walks one rotation step per calendar day after [today], matching
     * [ReconcileScheduleUseCase] which advances the pointer once per day.
     */
    private fun projectRolling(
        snapshot: ScheduleSnapshot,
        date: LocalDate,
        today: LocalDate,
    ): TrainingDay? {
        val ordered = snapshot.orderedDays()
        if (ordered.isEmpty() || !date.isAfter(today)) return null
        var index = snapshot.rollingIndex
        var cursor = today.plusDays(1)
        while (!cursor.isAfter(date)) {
            index = (index + 1).mod(ordered.size)
            if (cursor == date) return ordered[index]
            cursor = cursor.plusDays(1)
        }
        return null
    }

    private fun unresolved(date: LocalDate) = ResolvedDayPlan(
        date = date,
        day = null,
        commitment = DayPlanCommitment.UNRESOLVED,
        estimatedSeconds = 0,
    )
}
