package com.forge.hypertrophy.domain.engine

/**
 * A last-set RPE above 9 suggests a lighter next load. The suggestion is not
 * a new prescription: [weightAfterDecision] keeps [currentWeightKg] unless the
 * user accepts it.
 */
class AutoRegulationAdvisor {
    fun advise(lastRpe: Double?, currentWeightKg: Double?, incrementKg: Double): AutoRegulationSuggestion? {
        if (lastRpe == null || lastRpe <= RPE_THRESHOLD) return null
        if (currentWeightKg == null || incrementKg <= 0.0) return null
        val reduced = (currentWeightKg - incrementKg).coerceAtLeast(0.0)
        return AutoRegulationSuggestion(
            lastRpe = lastRpe,
            suggestedWeightKg = LoadRounding.roundToIncrement(reduced, incrementKg).coerceAtLeast(0.0),
        )
    }

    fun weightAfterDecision(
        currentWeightKg: Double,
        suggestion: AutoRegulationSuggestion?,
        accepted: Boolean,
    ): Double {
        if (!accepted || suggestion == null) return currentWeightKg
        return suggestion.suggestedWeightKg
    }

    private companion object {
        const val RPE_THRESHOLD = 9.0
    }
}

data class AutoRegulationSuggestion(
    val lastRpe: Double,
    val suggestedWeightKg: Double,
)
