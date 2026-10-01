package com.forge.hypertrophy.domain.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Monday that starts the review week containing [date]. */
fun reviewWeekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

data class DatedLift(
    val date: LocalDate,
    val lift: LiftSample,
)

data class DatedVolumeSet(
    val date: LocalDate,
    val set: VolumeSet,
)

data class StageAdvancement(
    val skillId: Long,
    val date: LocalDate,
    val fromTier: Int,
    val fromStage: Int,
    val toTier: Int,
    val toStage: Int,
)

/** Best estimated 1RM an exercise reached this week when it beat every earlier estimate. */
data class WeeklyPr(
    val exerciseId: Long,
    val date: LocalDate,
    val e1rmKg: Double,
    val weightKg: Double,
    val reps: Int,
)

data class MuscleWeekComparison(
    val muscle: String,
    val thisWeek: Double,
    val lastWeek: Double,
) {
    val delta: Double get() = thisWeek - lastWeek
}

data class WeeklyReview(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val sessionsCompleted: Int,
    val previousSessionsCompleted: Int,
    val prs: List<WeeklyPr>,
    val stageAdvancements: List<StageAdvancement>,
    val volume: List<MuscleWeekComparison>,
)

/**
 * One week of training, Monday to Sunday, next to the week before it.
 *
 * Sessions are counted per completed non-rest session. A PR is a lift inside
 * the week whose rounded e1RM beats every lift logged before it, using
 * [PrDetector]; the first time an exercise is ever lifted is not a PR. One
 * row per exercise keeps the best estimate of the week. Volume comes from
 * [WeeklyVolumeCalculator] for both weeks, and a muscle trained in either
 * week appears with zero for the other.
 */
class WeeklyReviewCalculator(
    private val prs: PrDetector = PrDetector(),
    private val volume: WeeklyVolumeCalculator = WeeklyVolumeCalculator(),
) {
    fun review(
        weekStart: LocalDate,
        completedSessionDates: List<LocalDate>,
        lifts: List<DatedLift>,
        sets: List<DatedVolumeSet>,
        advancements: List<StageAdvancement>,
    ): WeeklyReview {
        val start = reviewWeekStart(weekStart)
        val end = start.plusDays(6)
        val previousStart = start.minusWeeks(1)
        val previousEnd = start.minusDays(1)
        return WeeklyReview(
            weekStart = start,
            weekEnd = end,
            sessionsCompleted = completedSessionDates.count { it in start..end },
            previousSessionsCompleted = completedSessionDates.count { it in previousStart..previousEnd },
            prs = weeklyPrs(lifts, start, end),
            stageAdvancements = advancements.filter { it.date in start..end }.sortedWith(compareBy({ it.date }, { it.skillId })),
            volume = compareVolume(sets, start, end, previousStart, previousEnd),
        )
    }

    private fun weeklyPrs(lifts: List<DatedLift>, start: LocalDate, end: LocalDate): List<WeeklyPr> {
        val ordered = lifts.withIndex().sortedWith(compareBy({ it.value.date }, { it.index })).map { it.value }
        val history = mutableListOf<LiftSample>()
        val best = mutableMapOf<Long, WeeklyPr>()
        for (dated in ordered) {
            if (dated.date.isAfter(end)) break
            val lift = dated.lift
            if (!dated.date.isBefore(start) && prs.isNewPr(history, lift)) {
                val weight = lift.weightKg
                val reps = lift.reps
                val estimate = if (weight != null && reps != null) E1rmCalculator.epley(weight, reps) else null
                if (weight != null && reps != null && estimate != null) {
                    val current = best[lift.exerciseId]
                    if (current == null || estimate > current.e1rmKg) {
                        best[lift.exerciseId] = WeeklyPr(lift.exerciseId, dated.date, estimate, weight, reps)
                    }
                }
            }
            history += lift
        }
        return best.values.sortedWith(compareBy({ it.date }, { it.exerciseId }))
    }

    private fun compareVolume(
        sets: List<DatedVolumeSet>,
        start: LocalDate,
        end: LocalDate,
        previousStart: LocalDate,
        previousEnd: LocalDate,
    ): List<MuscleWeekComparison> {
        val current = volume.volume(sets.filter { it.date in start..end }.map { it.set })
        val previous = volume.volume(sets.filter { it.date in previousStart..previousEnd }.map { it.set })
        return (current.keys + previous.keys).sorted().map { muscle ->
            MuscleWeekComparison(muscle, current[muscle] ?: 0.0, previous[muscle] ?: 0.0)
        }
    }
}
