package com.forge.hypertrophy.domain.media

const val PRE_ROLL_SECONDS = 5
const val SELF_TIMER_SECONDS = 10

/**
 * Countdown before a recording or a photo. Each whole second left is an
 * audible beep, and zero is the moment capture starts.
 */
data class Countdown(
    val totalSeconds: Int,
    val startedAtElapsedMs: Long,
) {
    fun secondsLeft(nowElapsedMs: Long): Int {
        val elapsed = (nowElapsedMs - startedAtElapsedMs).coerceAtLeast(0)
        val left = totalSeconds - (elapsed / 1_000L).toInt()
        return left.coerceIn(0, totalSeconds)
    }

    fun finished(nowElapsedMs: Long): Boolean = secondsLeft(nowElapsedMs) == 0
}

/** The seconds announced by beeps, from [totalSeconds] down to 1. */
fun countdownBeeps(totalSeconds: Int): List<Int> = (totalSeconds.coerceAtLeast(0) downTo 1).toList()
