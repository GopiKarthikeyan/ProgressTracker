package com.forge.hypertrophy.domain.engine

/** Shoe retirement. A missing limit uses [DEFAULT_LIMIT_M], which is 700 km. */
object GearMileage {
    const val DEFAULT_LIMIT_M = 700_000

    fun limitMeters(configured: Int?): Int = configured ?: DEFAULT_LIMIT_M

    fun retired(distanceM: Double, configuredLimitM: Int?): Boolean {
        return distanceM >= limitMeters(configuredLimitM)
    }
}
