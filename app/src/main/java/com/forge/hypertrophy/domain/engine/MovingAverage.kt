package com.forge.hypertrophy.domain.engine

import java.time.LocalDate

data class DatedValue(
    val date: LocalDate,
    val value: Double,
)

/**
 * Trailing calendar-day average.
 *
 * A missing day is left out of both the sum and the count. It is not stored
 * as zero. A day whose window contains no samples produces no point. The
 * window is [windowDays] long and includes [DatedValue.date].
 */
object MovingAverage {
    fun trailing(samples: List<DatedValue>, windowDays: Int = WINDOW_DAYS): List<DatedValue> {
        if (windowDays <= 0) return emptyList()
        val byDate = linkedMapOf<LocalDate, Double>()
        for (sample in samples) {
            if (!sample.value.isFinite()) continue
            byDate[sample.date] = sample.value
        }
        if (byDate.isEmpty()) return emptyList()
        val start = byDate.keys.min()
        val end = byDate.keys.max()
        val span = (windowDays - 1).toLong()
        val result = mutableListOf<DatedValue>()
        var date = start
        while (!date.isAfter(end)) {
            val windowStart = date.minusDays(span)
            val values = byDate.filter { (sampleDate, _) ->
                !sampleDate.isBefore(windowStart) && !sampleDate.isAfter(date)
            }.values
            if (values.isNotEmpty()) {
                val mean = values.sum() / values.size.toDouble()
                result += DatedValue(date, LoadRounding.roundToDecimals(mean))
            }
            date = date.plusDays(1)
        }
        return result
    }

    const val WINDOW_DAYS = 7
}
