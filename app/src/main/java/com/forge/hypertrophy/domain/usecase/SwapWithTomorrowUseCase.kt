package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import java.time.Clock

/**
 * FIXED escape hatch. Exchanges the day scheduled today with the day
 * scheduled tomorrow. Nothing is completed, so the streak is unchanged.
 *
 * ROLLING is rejected. The snapshot passed in is not modified.
 */
class SwapWithTomorrowUseCase(
    private val clock: Clock,
    private val streaks: StreakCalculator = StreakCalculator(),
) {
    fun apply(snapshot: ScheduleSnapshot): ScheduleEdit {
        if (snapshot.mode != ScheduleMode.FIXED) return ScheduleEdit.Rejected
        val today = clock.localDate()
        val tomorrow = today.plusDays(1)
        val todayDay = snapshot.dayOn(today, today) ?: return ScheduleEdit.Rejected
        val tomorrowDay = snapshot.dayOn(tomorrow, today) ?: return ScheduleEdit.Rejected
        val updated = snapshot.copy(
            fixedSwaps = snapshot.fixedSwaps + (today to tomorrowDay.id) + (tomorrow to todayDay.id),
        )
        return ScheduleEdit.Applied(updated, streaks.streak(updated, today))
    }
}

sealed interface ScheduleEdit {
    data class Applied(val snapshot: ScheduleSnapshot, val streak: Int) : ScheduleEdit
    data object Rejected : ScheduleEdit
}
