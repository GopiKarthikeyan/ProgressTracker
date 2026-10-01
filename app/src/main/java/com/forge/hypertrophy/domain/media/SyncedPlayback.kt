package com.forge.hypertrophy.domain.media

const val FRAME_STEP_MS = 33L
const val SLOW_SPEED = 0.5f
const val NORMAL_SPEED = 1.0f

/**
 * Two clips played side by side. A shared position runs from zero to the
 * shortest aligned length; each clip adds its own start offset so the two
 * rep starts line up.
 */
data class SyncedPlayback(
    val leftDurationMs: Long,
    val rightDurationMs: Long,
    val leftOffsetMs: Long = 0,
    val rightOffsetMs: Long = 0,
    val positionMs: Long = 0,
    val playing: Boolean = false,
    val speed: Float = NORMAL_SPEED,
) {
    /** Shared timeline length after both offsets. */
    val lengthMs: Long
        get() = minOf(
            (leftDurationMs - leftOffsetMs).coerceAtLeast(0),
            (rightDurationMs - rightOffsetMs).coerceAtLeast(0),
        )

    val leftPositionMs: Long get() = positionMs + leftOffsetMs
    val rightPositionMs: Long get() = positionMs + rightOffsetMs

    fun seekTo(ms: Long): SyncedPlayback = copy(positionMs = ms.coerceIn(0, lengthMs))

    fun stepFrame(forward: Boolean): SyncedPlayback {
        val delta = if (forward) FRAME_STEP_MS else -FRAME_STEP_MS
        return copy(playing = false).seekTo(positionMs + delta)
    }

    fun togglePlaying(): SyncedPlayback = copy(playing = !playing)

    fun toggleSpeed(): SyncedPlayback = copy(speed = if (speed == SLOW_SPEED) NORMAL_SPEED else SLOW_SPEED)

    fun withLeftOffset(ms: Long): SyncedPlayback =
        copy(leftOffsetMs = ms.coerceIn(0, leftDurationMs)).seekTo(positionMs)

    fun withRightOffset(ms: Long): SyncedPlayback =
        copy(rightOffsetMs = ms.coerceIn(0, rightDurationMs)).seekTo(positionMs)

    /** Advances by wall-clock [elapsedMs] at the current speed and stops at the end. */
    fun advance(elapsedMs: Long): SyncedPlayback {
        if (!playing) return this
        val next = positionMs + (elapsedMs * speed).toLong()
        return if (next >= lengthMs) copy(positionMs = lengthMs, playing = false) else copy(positionMs = next)
    }
}
