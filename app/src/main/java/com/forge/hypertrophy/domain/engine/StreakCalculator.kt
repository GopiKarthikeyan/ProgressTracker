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
            when (mark(snapshot, date, today)) {
                DayMark.Satisfied -> count += 1
                DayMark.Missed, DayMark.Unknown -> return count
                DayMark.Open -> Unit
            }
            date = date.minusDays(1)
        }
        return count
    }

    /** Longest satisfied run inside the same lookback [streak] uses. */
    fun bestStreak(snapshot: ScheduleSnapshot, today: LocalDate): Int {
        var date = today
        var run = 0
        var best = 0
        var guard = 0
        while (guard++ < LOOKBACK_DAYS) {
            when (mark(snapshot, date, today)) {
                DayMark.Satisfied -> {
                    run += 1
                    if (run > best) best = run
                }
                DayMark.Missed -> run = 0
                DayMark.Open -> Unit
                DayMark.Unknown -> return best
            }
            date = date.minusDays(1)
        }
        return best
    }

    private fun mark(snapshot: ScheduleSnapshot, date: LocalDate, today: LocalDate): DayMark {
        val day = snapshot.dayOn(date, today) ?: return DayMark.Unknown
        val recorded = date in snapshot.explicitCompletions || date in snapshot.autoCompletedRests
        val ended = date.isBefore(today)
        return when {
            recorded -> DayMark.Satisfied
            day.isRest && ended -> DayMark.Satisfied
            day.isRest -> DayMark.Open
            ended -> DayMark.Missed
            else -> DayMark.Open
        }
    }

    private enum class DayMark {
        Satisfied,
        Missed,
        Open,
        Unknown,
    }

    private companion object {
        const val LOOKBACK_DAYS = 400
    }
}
