package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.engine.ReadinessAdvisor
import com.forge.hypertrophy.domain.engine.ReadinessCheck
import com.forge.hypertrophy.domain.engine.ReadinessAdvice
import com.forge.hypertrophy.domain.engine.SessionEstimator
import com.forge.hypertrophy.domain.engine.ShortOnTimePlanner
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.WorkoutPlan
import java.time.Clock

/**
 * Today's plan. [ReadinessAdvisor] decides whether weights are held.
 * [ShortOnTimePlanner] runs when that advice is to shorten the day or when
 * [forceShortOnTime] is set. [transitionRestSeconds] comes from the caller.
 */
class GetTodaysWorkoutUseCase(
    private val clock: Clock,
    private val readinessAdvisor: ReadinessAdvisor = ReadinessAdvisor(),
    private val planner: ShortOnTimePlanner = ShortOnTimePlanner(),
    private val estimator: SessionEstimator = SessionEstimator(),
) {
    fun today(
        snapshot: ScheduleSnapshot,
        check: ReadinessCheck,
        transitionRestSeconds: Int,
        budgetSeconds: Int,
        forceShortOnTime: Boolean = false,
    ): WorkoutPlan? {
        val date = clock.localDate()
        val day = snapshot.dayOn(date, date) ?: return null
        val advice = readinessAdvisor.advise(check)
        val shorten = forceShortOnTime || advice == ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS
        val slots = if (shorten) {
            planner.plan(day.slots, budgetSeconds, transitionRestSeconds)
        } else {
            day.slots
        }
        return WorkoutPlan(
            date = date,
            day = day,
            slots = slots,
            holdWeights = advice == ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS,
            estimatedSeconds = estimator.estimate(slots, transitionRestSeconds),
        )
    }
}
