package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.domain.model.PhysiqueMilestone
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Physique checks at 4 weeks, 12 weeks, and then every 6 months from [start].
 *
 * Week marks use [LocalDate.plusWeeks]. From 31 January that is 28 February
 * in both leap and common years. Month marks use [monthStep], which is
 * [LocalDate.plusMonths] and clamps to the last valid day: 31 January plus
 * one month is 29 February in a leap year and 28 February otherwise. The
 * 6-month series uses that same clamp.
 *
 * The closest photo is chosen inside a window that starts at ±7 days and
 * grows by 7 days until a photo fits. An empty library leaves [PhysiqueMilestone.photoOn]
 * null. Two photos at the same distance: the earlier date wins.
 */
class GetPhysiqueMilestonesUseCase(
    private val clock: Clock,
) {
    fun monthStep(start: LocalDate, months: Long): LocalDate = start.plusMonths(months)

    fun milestones(
        start: LocalDate,
        photos: List<LocalDate>,
        through: LocalDate? = null,
    ): List<PhysiqueMilestone> {
        val asOf = through ?: clock.localDate()
        return dueDates(start, asOf).map { due ->
            PhysiqueMilestone(dueOn = due, photoOn = closestPhoto(due, photos))
        }
    }

    private fun dueDates(start: LocalDate, asOf: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        listOf(start.plusWeeks(4), start.plusWeeks(12)).forEach { due ->
            if (!due.isAfter(asOf)) dates += due
        }
        var months = 6L
        while (months <= MAX_MONTHS) {
            val due = monthStep(start, months)
            if (due.isAfter(asOf)) break
            if (due !in dates) dates += due
            months += 6
        }
        return dates
    }

    private fun closestPhoto(due: LocalDate, photos: List<LocalDate>): LocalDate? {
        if (photos.isEmpty()) return null
        val farthest = photos.maxOf { abs(ChronoUnit.DAYS.between(due, it)) }
        var radius = WINDOW_START_DAYS
        while (true) {
            val inside = photos.filter { abs(ChronoUnit.DAYS.between(due, it)) <= radius }
            if (inside.isNotEmpty()) {
                return inside.minWith(
                    compareBy<LocalDate> { abs(ChronoUnit.DAYS.between(due, it)) }.thenBy { it },
                )
            }
            if (radius >= farthest) return null
            radius += WINDOW_STEP_DAYS
        }
    }

    private companion object {
        const val WINDOW_START_DAYS = 7L
        const val WINDOW_STEP_DAYS = 7L
        const val MAX_MONTHS = 12L * 40
    }
}
