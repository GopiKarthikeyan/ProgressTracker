package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import java.time.Clock

/**
 * Catch-up when the app is opened. This is a pure function of the snapshot
 * and the clock. Nothing here schedules background work.
 *
 * [ScheduleSnapshot.lastReconciled] is the last ended day already applied.
 * Each later day through yesterday is consumed. A null cursor does not
 * invent history: it starts at yesterday.
 *
 * ROLLING advances the pointer once per consumed day, including missed
 * training days, so a long absence catches the rotation up. Rest days in
 * that span are auto-completed. FIXED keeps the weekday mapping and only
 * auto-completes rest weekdays that ended.
 */
class ReconcileScheduleUseCase(
    private val clock: Clock,
) {
    fun reconcile(snapshot: ScheduleSnapshot): ScheduleSnapshot {
        val today = clock.localDate()
        val yesterday = today.minusDays(1)
        val last = snapshot.lastReconciled ?: return snapshot.copy(lastReconciled = yesterday)
        if (!last.isBefore(yesterday)) return snapshot

        val ordered = snapshot.orderedDays()
        var index = snapshot.rollingIndex
        val rests = snapshot.autoCompletedRests.toMutableSet()
        var date = last.plusDays(1)
        while (!date.isAfter(yesterday)) {
            val day = when (snapshot.mode) {
                ScheduleMode.FIXED -> snapshot.dayOn(date, today)
                ScheduleMode.ROLLING -> {
                    if (ordered.isEmpty()) null
                    else {
                        val current = ordered[index.mod(ordered.size)]
                        index = (index + 1).mod(ordered.size)
                        current
                    }
                }
            }
            if (day != null && day.isRest && date !in snapshot.explicitCompletions) {
                rests += date
            }
            date = date.plusDays(1)
        }
        return snapshot.copy(
            rollingIndex = if (snapshot.mode == ScheduleMode.ROLLING) index else snapshot.rollingIndex,
            lastReconciled = yesterday,
            autoCompletedRests = rests,
        )
    }
}
