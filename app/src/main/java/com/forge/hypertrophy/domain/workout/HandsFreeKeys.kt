package com.forge.hypertrophy.domain.workout

/**
 * Hardware triggers for the workout screen. Values match the platform key
 * codes: volume keys, the camera key most Bluetooth shutters send, and the
 * headset and media-play keys.
 *
 * Headset and media-play keys [yieldsToActiveMedia] so the screen can leave
 * them alone while another app holds media focus. Volume and shutter keys
 * always belong to the workout.
 */
object HandsFreeKeys {
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val CAMERA = 27
    const val HEADSETHOOK = 79
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_PLAY = 126

    private val workoutKeys = setOf(
        VOLUME_UP,
        VOLUME_DOWN,
        CAMERA,
        HEADSETHOOK,
        MEDIA_PLAY_PAUSE,
        MEDIA_PLAY,
    )

    private val mediaKeys = setOf(HEADSETHOOK, MEDIA_PLAY_PAUSE, MEDIA_PLAY)

    fun isWorkoutKey(keyCode: Int): Boolean = keyCode in workoutKeys

    fun yieldsToActiveMedia(keyCode: Int): Boolean = keyCode in mediaKeys
}
