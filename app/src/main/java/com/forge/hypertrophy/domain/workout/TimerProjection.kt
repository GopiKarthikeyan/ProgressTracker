package com.forge.hypertrophy.domain.workout

fun interface ElapsedRealtimeClock {
    /** Milliseconds since boot, matching [android.os.SystemClock.elapsedRealtime]. */
    fun elapsedRealtime(): Long
}

enum class TimerKind {
    COUNTDOWN,
    STOPWATCH,
}

/**
 * Countdown anchor is the elapsedRealtime when remaining hits zero.
 * Overtime runs until [overtimeEndElapsedRealtime]. A stopwatch anchor is the start.
 */
data class TimerSpec(
    val kind: TimerKind,
    val anchorElapsedRealtime: Long,
    val overtimeEndElapsedRealtime: Long? = null,
    val cue: String? = null,
    val speak: Boolean = false,
) {
    fun persistedEnd(): Long? = if (kind == TimerKind.COUNTDOWN) anchorElapsedRealtime else null

    fun adjust(deltaMillis: Long): TimerSpec {
        if (kind != TimerKind.COUNTDOWN || deltaMillis == 0L) return this
        return copy(
            anchorElapsedRealtime = anchorElapsedRealtime + deltaMillis,
            overtimeEndElapsedRealtime = overtimeEndElapsedRealtime?.plus(deltaMillis),
        )
    }
}

enum class TimerPhase {
    IDLE,
    COUNTDOWN,
    WARNING,
    OVERTIME,
    STOPWATCH,
    FINISHED,
}

data class TimerSnapshot(
    val phase: TimerPhase,
    val remainingMillis: Long = 0,
    val elapsedMillis: Long = 0,
    val cue: String? = null,
    val speak: Boolean = false,
) {
    companion object {
        val Idle = TimerSnapshot(TimerPhase.IDLE)
    }
}

const val REST_WARNING_MILLIS = 15_000L
const val TIMER_STEP_MILLIS = 30_000L

fun projectTimer(spec: TimerSpec?, nowElapsedRealtime: Long): TimerSnapshot {
    if (spec == null) return TimerSnapshot.Idle
    return when (spec.kind) {
        TimerKind.STOPWATCH -> TimerSnapshot(
            phase = TimerPhase.STOPWATCH,
            elapsedMillis = (nowElapsedRealtime - spec.anchorElapsedRealtime).coerceAtLeast(0),
            cue = spec.cue,
            speak = spec.speak,
        )
        TimerKind.COUNTDOWN -> {
            val remaining = spec.anchorElapsedRealtime - nowElapsedRealtime
            when {
                remaining > REST_WARNING_MILLIS -> TimerSnapshot(
                    phase = TimerPhase.COUNTDOWN,
                    remainingMillis = remaining,
                    cue = spec.cue,
                    speak = spec.speak,
                )
                remaining > 0L -> TimerSnapshot(
                    phase = TimerPhase.WARNING,
                    remainingMillis = remaining,
                    cue = spec.cue,
                    speak = spec.speak,
                )
                else -> {
                    val overtimeEnd = spec.overtimeEndElapsedRealtime
                    if (overtimeEnd != null && nowElapsedRealtime < overtimeEnd) {
                        TimerSnapshot(
                            phase = TimerPhase.OVERTIME,
                            elapsedMillis = nowElapsedRealtime - spec.anchorElapsedRealtime,
                            cue = spec.cue,
                            speak = spec.speak,
                        )
                    } else {
                        TimerSnapshot(phase = TimerPhase.FINISHED)
                    }
                }
            }
        }
    }
}

/** How long a partial wake lock may be held for this spec. Never zero. */
fun wakeLockTimeoutMillis(spec: TimerSpec, nowElapsedRealtime: Long): Long {
    val deadline = when (spec.kind) {
        TimerKind.COUNTDOWN -> spec.overtimeEndElapsedRealtime ?: spec.anchorElapsedRealtime
        TimerKind.STOPWATCH -> nowElapsedRealtime + STOPWATCH_WAKE_CAP_MILLIS
    }
    return (deadline - nowElapsedRealtime).coerceAtLeast(1)
}

private const val STOPWATCH_WAKE_CAP_MILLIS = 30L * 60L * 1000L

fun countdownSpec(
    nowElapsedRealtime: Long,
    minimumSeconds: Int,
    maximumSeconds: Int,
    cue: String? = null,
    speak: Boolean = false,
): TimerSpec {
    val minMillis = minimumSeconds.coerceAtLeast(0) * 1_000L
    val maxMillis = maximumSeconds.coerceAtLeast(minimumSeconds) * 1_000L
    val end = nowElapsedRealtime + minMillis
    return TimerSpec(
        kind = TimerKind.COUNTDOWN,
        anchorElapsedRealtime = end,
        overtimeEndElapsedRealtime = end + (maxMillis - minMillis),
        cue = cue,
        speak = speak,
    )
}

fun restoredCountdown(
    storedEndElapsedRealtime: Long,
    minimumSeconds: Int,
    maximumSeconds: Int,
): TimerSpec {
    val windowMillis = (maximumSeconds.coerceAtLeast(minimumSeconds) - minimumSeconds.coerceAtLeast(0)) * 1_000L
    return TimerSpec(
        kind = TimerKind.COUNTDOWN,
        anchorElapsedRealtime = storedEndElapsedRealtime,
        overtimeEndElapsedRealtime = storedEndElapsedRealtime + windowMillis,
    )
}

fun stopwatchSpec(nowElapsedRealtime: Long): TimerSpec = TimerSpec(
    kind = TimerKind.STOPWATCH,
    anchorElapsedRealtime = nowElapsedRealtime,
)

