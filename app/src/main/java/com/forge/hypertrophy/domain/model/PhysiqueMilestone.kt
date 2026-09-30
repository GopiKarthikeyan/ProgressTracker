package com.forge.hypertrophy.domain.model

import java.time.LocalDate

/** A milestone date and the closest photo the expanding window could find. */
data class PhysiqueMilestone(
    val dueOn: LocalDate,
    val photoOn: LocalDate?,
)
