package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ProgressionSuggestion
import com.forge.hypertrophy.domain.model.SetType

/**
 * Per-slot double progression.
 *
 * The load that progresses is the heaviest [SetType.WORKING] set. Lighter
 * working sets are back-offs: they do not set the load and they do not decide
 * the rep target. [SetType.AMRAP] sets still count toward the rep target, and
 * they never set the load.
 *
 * [ProgressionRule.DOUBLE] and [ProgressionRule.LINEAR] share rep gates.
 * Every governing set at [ProgressionInput.repsHigh] increases. Every governing
 * set below [ProgressionInput.repsLow] decreases. Anything in between holds.
 * When low == high that middle band is empty, so a fixed target has no hold:
 * a hit increases and a miss decreases. When low < high the band is real, for
 * LINEAR as well as DOUBLE, so a mid-range LINEAR slot can still stall.
 * [ProgressionRule.NONE] never changes the load.
 *
 * [Equipment.WEIGHTED_BODYWEIGHT] is its own branch. [LoggedSet.weightKg] on
 * that equipment is added load only, never bodyweight plus added load, and a
 * decrease stops at 0 kg.
 *
 * A session with no working sets is skipped. A stall is a mid-range latest
 * session whose working weight is not greater than the working weight three
 * sessions back (the oldest of the three most recent kept sessions). A dip
 * that is later undone, and a regression, are both no progress. Stall is a
 * suggestion and does not change the stored prescription. It is counted only
 * on the sessions passed in. When a deload ends, the caller must pass
 * post-deload sessions only, so the three flat sessions that led into the
 * deload do not immediately suggest a variation again.
 */
class DoubleProgressionEngine {
    fun suggest(input: ProgressionInput): ProgressionSuggestion {
        val meaningful = input.slotSessions.map { session ->
            session.sets.filter { it.setType == SetType.WORKING || it.setType == SetType.AMRAP }
        }.filter { it.isNotEmpty() }

        if (meaningful.isEmpty()) {
            val cold = input.latestWeightFromAnySlotKg ?: return hold()
            if (isBodyweightReps(input)) return ProgressionSuggestion(ProgressionAction.COLD_START, null)
            return ProgressionSuggestion(
                ProgressionAction.COLD_START,
                roundLoad(cold, incrementKg(input)),
            )
        }

        if (input.rule == ProgressionRule.NONE) return hold()

        val latest = meaningful.first()
        val increment = incrementKg(input)
        val current = workingWeight(latest)

        if (isBodyweightReps(input)) {
            return when {
                hitIncreaseTarget(latest, input) ->
                    ProgressionSuggestion(ProgressionAction.VARIATION_OR_ADDED_LOAD, null)
                allBelowBottom(latest, input) ->
                    ProgressionSuggestion(ProgressionAction.DECREASE, null)
                stalled(meaningful, increment) ->
                    ProgressionSuggestion(ProgressionAction.STALL, null)
                else -> hold()
            }
        }

        if (isWeightedBodyweight(input)) {
            return when {
                hitIncreaseTarget(latest, input) ->
                    ProgressionSuggestion(
                        ProgressionAction.INCREASE,
                        roundLoad(current + increment, increment),
                    )
                allBelowBottom(latest, input) ->
                    ProgressionSuggestion(
                        ProgressionAction.DECREASE,
                        roundLoad((current - increment).coerceAtLeast(0.0), increment),
                    )
                stalled(meaningful, increment) ->
                    ProgressionSuggestion(ProgressionAction.STALL, null)
                else -> hold()
            }
        }

        return when {
            hitIncreaseTarget(latest, input) ->
                ProgressionSuggestion(
                    ProgressionAction.INCREASE,
                    roundLoad(current + increment, increment),
                )
            allBelowBottom(latest, input) ->
                ProgressionSuggestion(
                    ProgressionAction.DECREASE,
                    roundLoad((current - increment).coerceAtLeast(0.0), increment),
                )
            stalled(meaningful, increment) ->
                ProgressionSuggestion(ProgressionAction.STALL, null)
            else -> hold()
        }
    }

    private fun hitIncreaseTarget(sets: List<LoggedSet>, input: ProgressionInput): Boolean {
        return setsForRepTargets(sets).all { (it.reps ?: 0) >= input.repsHigh }
    }

    private fun allBelowBottom(sets: List<LoggedSet>, input: ProgressionInput): Boolean {
        return setsForRepTargets(sets).all { (it.reps ?: 0) < input.repsLow }
    }

    /**
     * Heaviest working sets decide the rep target. Back-off sets are lighter
     * and are left out. AMRAP sets are always included.
     */
    private fun setsForRepTargets(sets: List<LoggedSet>): List<LoggedSet> {
        val working = sets.filter { it.setType == SetType.WORKING }
        val amrap = sets.filter { it.setType == SetType.AMRAP }
        if (working.isEmpty()) return amrap
        val top = LoadRounding.roundToDecimals(workingWeight(sets))
        val topWorking = working.filter { set ->
            LoadRounding.roundToDecimals(set.weightKg ?: 0.0) == top
        }
        return topWorking + amrap
    }

    private fun stalled(sessions: List<List<LoggedSet>>, increment: Double): Boolean {
        if (sessions.size < STALL_SESSIONS) return false
        val recent = roundLoad(workingWeight(sessions.first()), increment)
        val threeBack = roundLoad(workingWeight(sessions[STALL_SESSIONS - 1]), increment)
        return recent <= threeBack
    }

    /** Heaviest [SetType.WORKING] set. AMRAP never supplies this weight. */
    private fun workingWeight(sets: List<LoggedSet>): Double {
        val working = sets.filter { it.setType == SetType.WORKING }
        val counted = working.ifEmpty { sets }
        return counted.maxOf { it.weightKg ?: 0.0 }
    }

    private fun incrementKg(input: ProgressionInput): Double {
        return input.incrementOverrideKg ?: input.exerciseIncrementKg
    }

    private fun isBodyweightReps(input: ProgressionInput): Boolean {
        return input.equipment == Equipment.BODYWEIGHT && input.metricType == MetricType.REPS
    }

    private fun isWeightedBodyweight(input: ProgressionInput): Boolean {
        return input.equipment == Equipment.WEIGHTED_BODYWEIGHT
    }

    private fun roundLoad(kg: Double, incrementKg: Double): Double {
        return LoadRounding.roundToIncrement(kg, incrementKg)
    }

    private fun hold() = ProgressionSuggestion(ProgressionAction.HOLD, null)

    private companion object {
        const val STALL_SESSIONS = 3
    }
}
