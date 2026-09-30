package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.engine.StreakCalculator
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import java.time.Clock

/**
 * ROLLING escape hatch. Pulls the next rest day to today, records today as
 * an auto-completed rest, and leaves the training days that were in front of
 * that rest queued behind it.
 *
 * FIXED is rejected. The snapshot passed in is not modified.
 */
class TakeRestNowUseCase(
    private val clock: Clock,
    private val streaks: StreakCalculator = StreakCalculator(),
) {
    fun apply(snapshot: ScheduleSnapshot): ScheduleEdit {
        if (snapshot.mode != ScheduleMode.ROLLING) return ScheduleEdit.Rejected
        val today = clock.localDate()
        val ordered = snapshot.orderedDays()
        if (ordered.isEmpty()) return ScheduleEdit.Rejected
        val start = snapshot.rollingIndex.mod(ordered.size)
        val fromHere = (0 until ordered.size).map { ordered[(start + it).mod(ordered.size)] }
        val restOffset = fromHere.indexOfFirst { it.isRest }
        if (restOffset < 0) return ScheduleEdit.Rejected
        val rotated = buildList {
            add(fromHere[restOffset])
            fromHere.forEachIndexed { index, day -> if (index != restOffset) add(day) }
        }.mapIndexed { index, day -> day.copy(sequenceIndex = index) }
        val rest = rotated.first()
        val updated = snapshot.copy(
            days = rotated,
            rollingIndex = if (rotated.size == 1) 0 else 1,
            autoCompletedRests = snapshot.autoCompletedRests + today,
            rollingDayByDate = snapshot.rollingDayByDate + (today to rest.id),
        )
        return ScheduleEdit.Applied(updated, streaks.streak(updated, today))
    }
}
