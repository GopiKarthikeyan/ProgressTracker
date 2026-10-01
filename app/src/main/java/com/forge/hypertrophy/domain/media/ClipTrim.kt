package com.forge.hypertrophy.domain.media

const val DEFAULT_LEAD_TRIM_MS = 0L
const val DEFAULT_TAIL_TRIM_MS = 3_000L

/** The part of a recorded clip that is kept, in milliseconds from the start. */
data class TrimWindow(
    val startMs: Long,
    val endMs: Long,
) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * Cuts [leadMs] from the head and [tailMs] from the end of a clip that lasts
 * [durationMs]. The trims are clamped so the window always stays inside the
 * clip, and a clip too short for both trims is kept whole.
 */
fun trimWindow(
    durationMs: Long,
    leadMs: Long = DEFAULT_LEAD_TRIM_MS,
    tailMs: Long = DEFAULT_TAIL_TRIM_MS,
): TrimWindow {
    val duration = durationMs.coerceAtLeast(0)
    val lead = leadMs.coerceIn(0, duration)
    val tail = tailMs.coerceIn(0, duration)
    val start = lead
    val end = duration - tail
    if (end <= start) return TrimWindow(0, duration)
    return TrimWindow(start, end)
}
