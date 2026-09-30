package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import java.time.LocalDate

/**
 * Consecutive satisfied days ending at [today], in both schedule modes.
 *
 * A rest day whose calendar day has ended is satisfied even when nobody
 * logged it. A missed rest therefore does not break the streak. A training
 * day that has ended without a completion does. Today is still open: it
 * counts only when it was completed or recorded as an auto-completed rest,
 * and an open today does not break the run.
 *
 * FIXED reads [com.forge.hypertrophy.domain.model.TrainingDay.dayOfWeek] and
 * [ScheduleSnapshot.fixedSwaps]. ROLLING reads [ScheduleSnapshot.rollingDayByDate]
 * and does not fall back to the weekday.
 */
class StreakCalculator {
    fun streak(snapshot: ScheduleSnapshot, today: LocalDate): Int {
        var date = today
        var count = 0
        var guard = 0
        while (guard++ < LOOKBACK_DAYS) {
            val day = snapshot.dayOn(date, today) ?: return count
            val recorded = date in snapshot.explicitCompletions || date in snapshot.autoCompletedRests
            val ended = date.isBefore(today)
            val satisfied = when {
                recorded -> true
                day.isRest && ended -> true
                day.isRest -> null
                ended -> false
                else -> null
            }
            when (satisfied) {
                true -> count += 1
                false -> return count
                null -> Unit
            }
            date = date.minusDays(1)
        }
        return count
    }

    private companion object {
        const val LOOKBACK_DAYS = 400
    }
}
