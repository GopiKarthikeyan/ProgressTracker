package com.forge.hypertrophy.domain.model

import java.time.LocalDate

data class WorkoutPlan(
    val date: LocalDate,
    val day: TrainingDay,
    val slots: List<TrainingSlot>,
    val holdWeights: Boolean,
    val estimatedSeconds: Int,
)
