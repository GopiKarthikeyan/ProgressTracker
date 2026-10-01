package com.forge.hypertrophy.domain.media

/**
 * Text burned into a clip. A hold reads "Tuck Planche: 15s", a loaded set
 * reads "Deadlift 140kg × 5", and a bodyweight set reads "Pull-ups × 8".
 * The exercise name is data; nothing here is a fixed exercise.
 */
fun setLabel(
    exerciseName: String,
    weightKg: Double?,
    reps: Int?,
    holdSec: Int?,
): String {
    val name = exerciseName.trim()
    if (holdSec != null && holdSec > 0) return "$name: ${holdSec}s"
    val parts = mutableListOf(name)
    if (weightKg != null && weightKg > 0.0) parts += "${formatKilograms(weightKg)}kg"
    if (reps != null && reps > 0) parts += "× $reps"
    return parts.joinToString(" ")
}

private fun formatKilograms(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
