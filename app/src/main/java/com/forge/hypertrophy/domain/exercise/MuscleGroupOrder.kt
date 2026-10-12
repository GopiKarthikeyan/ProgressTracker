package com.forge.hypertrophy.domain.exercise

/**
 * Stable primary-muscle ordering for exercise pickers.
 * Unknown / empty muscles sort last under [OTHER].
 */
object MuscleGroupOrder {
    const val OTHER = "OTHER"

    private val order = listOf(
        "CHEST",
        "BACK_LATS",
        "BACK_UPPER",
        "SHOULDERS_FRONT",
        "SHOULDERS_SIDE",
        "SHOULDERS_REAR",
        "BICEPS",
        "TRICEPS",
        "FOREARMS",
        "CORE",
        "QUADS",
        "HAMSTRINGS",
        "GLUTES",
        "ADDUCTORS",
        "CALVES",
        "LOWER_BACK",
        OTHER,
    )

    private val rank = order.withIndex().associate { (i, key) -> key to i }

    fun primarySortKey(primaryMuscles: List<String>): String {
        val first = primaryMuscles.firstOrNull()?.uppercase()?.takeIf { it in rank }
        return first ?: OTHER
    }

    fun compare(a: List<String>, b: List<String>): Int {
        val ka = primarySortKey(a)
        val kb = primarySortKey(b)
        return (rank.getValue(ka)).compareTo(rank.getValue(kb))
    }

    fun orderedKeys(): List<String> = order
}
