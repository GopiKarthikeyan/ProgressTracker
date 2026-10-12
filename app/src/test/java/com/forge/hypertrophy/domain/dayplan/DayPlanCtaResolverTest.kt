package com.forge.hypertrophy.domain.dayplan

import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.usecase.DayPlanCommitment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayPlanCtaResolverTest {
    private val today = LocalDate.of(2026, 3, 16)

    @Test
    fun todayTrainingStartsWorkout() {
        val cta = DayPlanCtaResolver.resolve(
            selectedDate = today,
            today = today,
            commitment = DayPlanCommitment.COMMITTED,
            isRest = false,
            mode = ScheduleMode.FIXED,
            hasActiveSession = false,
            swapPeerWeekday = "Wed",
        )
        assertEquals(DayPlanCtaKind.START_WORKOUT, cta.kind)
    }

    @Test
    fun todayRestRollingTakesRest() {
        val cta = DayPlanCtaResolver.resolve(
            selectedDate = today,
            today = today,
            commitment = DayPlanCommitment.COMMITTED,
            isRest = true,
            mode = ScheduleMode.ROLLING,
            hasActiveSession = false,
            swapPeerWeekday = null,
        )
        assertEquals(DayPlanCtaKind.TAKE_REST, cta.kind)
    }

    @Test
    fun futureFixedSwapsWithToday() {
        val cta = DayPlanCtaResolver.resolve(
            selectedDate = today.plusDays(2),
            today = today,
            commitment = DayPlanCommitment.COMMITTED,
            isRest = false,
            mode = ScheduleMode.FIXED,
            hasActiveSession = false,
            swapPeerWeekday = null,
        )
        assertEquals(DayPlanCtaKind.SWAP_WITH_TODAY, cta.kind)
    }

    @Test
    fun projectedShowsProjectedCta() {
        val cta = DayPlanCtaResolver.resolve(
            selectedDate = today.plusDays(1),
            today = today,
            commitment = DayPlanCommitment.PROJECTED,
            isRest = false,
            mode = ScheduleMode.ROLLING,
            hasActiveSession = false,
            swapPeerWeekday = null,
        )
        assertEquals(DayPlanCtaKind.PROJECTED, cta.kind)
    }
}
