package com.forge.hypertrophy.domain.media

/**
 * Hardware keys that start and stop a recording. Volume keys cover the phone
 * and most Bluetooth shutter remotes; the camera key covers remotes that
 * send it instead.
 */
object CaptureKeys {
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val CAMERA = 27

    private val codes = setOf(VOLUME_UP, VOLUME_DOWN, CAMERA)

    fun isCaptureKey(keyCode: Int): Boolean = keyCode in codes
}
