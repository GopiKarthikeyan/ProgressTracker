package com.forge.hypertrophy.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

data class TrainingSlot(
    val id: Long,
    val prescription: SlotPrescription,
)

data class TrainingDay(
    val id: Long,
    val label: String,
    val dayOfWeek: DayOfWeek?,
    val sequenceIndex: Int,
    val isRest: Boolean,
    val slots: List<TrainingSlot>,
)

/**
 * Schedule cursor the use cases pass around. No repository and no clock:
 * the caller supplies [today] when a date is required.
 *
 * [lastReconciled] is the last calendar day whose end has already been
 * applied. [rollingDayByDate] is the ROLLING assignment for dates that have
 * been resolved. [fixedSwaps] exchanges two FIXED dates.
 */
data class ScheduleSnapshot(
    val mode: ScheduleMode,
    val days: List<TrainingDay>,
    val rollingIndex: Int = 0,
    val lastReconciled: LocalDate? = null,
    val explicitCompletions: Set<LocalDate> = emptySet(),
    val autoCompletedRests: Set<LocalDate> = emptySet(),
    val rollingDayByDate: Map<LocalDate, Long> = emptyMap(),
    val fixedSwaps: Map<LocalDate, Long> = emptyMap(),
) {
    fun orderedDays(): List<TrainingDay> = days.sortedBy { it.sequenceIndex }

    /**
     * Day scheduled on [date]. A past ROLLING date missing from
     * [rollingDayByDate] is unresolved. [today] with no mapping uses the
     * rolling pointer.
     */
    fun dayOn(date: LocalDate, today: LocalDate): TrainingDay? {
        val ordered = orderedDays()
        if (ordered.isEmpty()) return null
        return when (mode) {
            ScheduleMode.FIXED -> {
                val swapped = fixedSwaps[date]
                if (swapped != null) {
                    ordered.firstOrNull { it.id == swapped }
                } else {
                    ordered.firstOrNull { it.dayOfWeek == date.dayOfWeek }
                }
            }
            ScheduleMode.ROLLING -> {
                val mapped = rollingDayByDate[date]
                val id = when {
                    mapped != null -> mapped
                    date == today -> ordered[rollingIndex.mod(ordered.size)].id
                    else -> return null
                }
                ordered.firstOrNull { it.id == id }
            }
        }
    }
}
