package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import java.time.Clock
import java.time.LocalDate

/**
 * FIXED escape hatch. Exchanges the day scheduled on [selectedDate] with the
 * day scheduled today. ROLLING and same-day / missing days are rejected.
 */
class SwapWithTodayUseCase(
    private val clock: Clock,
    private val streaks: StreakCalculator = StreakCalculator(),
) {
    fun apply(snapshot: ScheduleSnapshot, selectedDate: LocalDate): ScheduleEdit {
        if (snapshot.mode != ScheduleMode.FIXED) return ScheduleEdit.Rejected
        val today = clock.localDate()
        if (selectedDate == today) return ScheduleEdit.Rejected
        val todayDay = snapshot.dayOn(today, today) ?: return ScheduleEdit.Rejected
        val selectedDay = snapshot.dayOn(selectedDate, today) ?: return ScheduleEdit.Rejected
        val updated = snapshot.copy(
            fixedSwaps = snapshot.fixedSwaps +
                (today to selectedDay.id) +
                (selectedDate to todayDay.id),
        )
        return ScheduleEdit.Applied(updated, streaks.streak(updated, today))
    }
}
