package com.forge.hypertrophy.widget

import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.domain.engine.StreakCalculator
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** What the home-screen widget shows. [dayLabel] is null without a program or a day scheduled today. */
data class TodaySummary(
    val dayLabel: String?,
    val isRest: Boolean,
    val streak: Int,
    val completedToday: Boolean,
    val inProgress: Boolean,
) {
    companion object {
        val Empty = TodaySummary(dayLabel = null, isRest = false, streak = 0, completedToday = false, inProgress = false)
    }
}

@Singleton
class TodaySummaryProvider @Inject constructor(
    private val loader: ScheduleLoader,
    private val sessions: SessionRepository,
    private val clock: Clock,
) {
    private val streaks = StreakCalculator()

    suspend fun summary(): TodaySummary {
        val loaded = loader.load() ?: return TodaySummary.Empty
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val snapshot = loaded.snapshot
        val day = snapshot.dayOn(today, today)
        return TodaySummary(
            dayLabel = day?.label,
            isRest = day?.isRest == true,
            streak = streaks.streak(snapshot, today),
            completedToday = today in snapshot.explicitCompletions || today in snapshot.autoCompletedRests,
            inProgress = sessions.observeInProgress().first().isNotEmpty(),
        )
    }
}
