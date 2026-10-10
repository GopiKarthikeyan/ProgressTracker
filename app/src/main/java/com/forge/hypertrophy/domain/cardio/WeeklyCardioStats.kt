package com.forge.hypertrophy.domain.cardio

import com.forge.hypertrophy.domain.model.CardioActivity
import java.time.LocalDate

data class CardioLogSample(
    val date: LocalDate,
    val activity: CardioActivity,
    val distanceM: Double,
    val durationSec: Int,
)

data class CardioActivityBreakdown(
    val activity: CardioActivity,
    val sessions: Int,
    val durationSec: Int,
    val distanceM: Double,
)

data class WeeklyCardioStats(
    val sessions: Int,
    val durationSec: Int,
    val distanceM: Double,
    val byActivity: List<CardioActivityBreakdown>,
)

fun weeklyCardioStats(
    logs: List<CardioLogSample>,
    weekStart: LocalDate,
    weekEnd: LocalDate,
): WeeklyCardioStats {
    val week = logs.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
    val byActivity = week.groupBy { it.activity }
        .map { (activity, rows) ->
            CardioActivityBreakdown(
                activity = activity,
                sessions = rows.size,
                durationSec = rows.sumOf { it.durationSec },
                distanceM = rows.sumOf { it.distanceM.coerceAtLeast(0.0) },
            )
        }
        .sortedBy { it.activity.name }
    return WeeklyCardioStats(
        sessions = week.size,
        durationSec = week.sumOf { it.durationSec },
        distanceM = week.sumOf { it.distanceM.coerceAtLeast(0.0) },
        byActivity = byActivity,
    )
}
