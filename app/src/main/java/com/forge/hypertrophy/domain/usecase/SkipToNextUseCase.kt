package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import java.time.Clock

/**
 * ROLLING escape hatch. Advances the pointer to the next day and does not
 * record today as completed, so the streak does not gain today and does not
 * lose the days already satisfied.
 *
 * FIXED is rejected. The snapshot passed in is not modified.
 */
class SkipToNextUseCase(
    private val clock: Clock,
    private val streaks: StreakCalculator = StreakCalculator(),
) {
    fun apply(snapshot: ScheduleSnapshot): ScheduleEdit {
        if (snapshot.mode != ScheduleMode.ROLLING) return ScheduleEdit.Rejected
        val ordered = snapshot.orderedDays()
        if (ordered.isEmpty()) return ScheduleEdit.Rejected
        val today = clock.localDate()
        val next = (snapshot.rollingIndex + 1).mod(ordered.size)
        val updated = snapshot.copy(
            rollingIndex = next,
            rollingDayByDate = snapshot.rollingDayByDate + (today to ordered[next].id),
        )
        return ScheduleEdit.Applied(updated, streaks.streak(updated, today))
    }
}
