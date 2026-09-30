package com.forge.hypertrophy.domain.model

data class PlateLoad(
    val barKg: Double,
    val platesPerSideKg: List<Double>,
    val totalKg: Double,
    val requestedKg: Double,
    val exactMatch: Boolean,
)

data class WarmupStep(
    val weightKg: Double,
    val reps: Int,
    val platesPerSideKg: List<Double>,
)
