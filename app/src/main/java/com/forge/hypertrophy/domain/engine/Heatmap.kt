package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SessionKind

/**
 * One color per day. A gym session wins over cardio on the same date, then
 * active recovery, then rest. A scheduled rest with no logged session stays
 * empty so a fresh install does not paint the calendar.
 */
fun heatmapKind(sessionKinds: Set<SessionKind>): SessionKind? {
    for (kind in PRIORITY) {
        if (kind in sessionKinds) return kind
    }
    return null
}

private val PRIORITY = listOf(
    SessionKind.GYM,
    SessionKind.CARDIO,
    SessionKind.ACTIVE_RECOVERY,
    SessionKind.REST,
)
