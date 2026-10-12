package com.forge.hypertrophy.domain.dayplan

import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.usecase.DayPlanCommitment
import java.time.LocalDate

enum class DayPlanCtaKind {
    START_WORKOUT,
    TAKE_REST,
    SWAP_WITH_TODAY,
    PROJECTED,
    NONE,
}

data class DayPlanCta(
    val kind: DayPlanCtaKind,
    /** Short weekday name for swap label, e.g. "Wed". */
    val swapPeerLabel: String? = null,
)

object DayPlanCtaResolver {
    fun resolve(
        selectedDate: LocalDate,
        today: LocalDate,
        commitment: DayPlanCommitment,
        isRest: Boolean,
        mode: ScheduleMode,
        hasActiveSession: Boolean,
        swapPeerWeekday: String?,
    ): DayPlanCta {
        if (commitment == DayPlanCommitment.PROJECTED) {
            return DayPlanCta(DayPlanCtaKind.PROJECTED)
        }
        if (commitment == DayPlanCommitment.UNRESOLVED) {
            return DayPlanCta(DayPlanCtaKind.NONE)
        }
        if (selectedDate == today) {
            if (hasActiveSession && !isRest) return DayPlanCta(DayPlanCtaKind.START_WORKOUT)
            if (isRest) {
                return if (mode == ScheduleMode.ROLLING) {
                    DayPlanCta(DayPlanCtaKind.TAKE_REST)
                } else {
                    DayPlanCta(DayPlanCtaKind.NONE)
                }
            }
            return DayPlanCta(DayPlanCtaKind.START_WORKOUT)
        }
        if (selectedDate.isAfter(today) && mode == ScheduleMode.FIXED) {
            return DayPlanCta(DayPlanCtaKind.SWAP_WITH_TODAY, swapPeerLabel = swapPeerWeekday)
        }
        return DayPlanCta(DayPlanCtaKind.NONE)
    }
}
