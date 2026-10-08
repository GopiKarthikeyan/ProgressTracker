package com.forge.hypertrophy.domain.workout

/** Body regions a lifter can flag for the rest of the session. Stored by these keys. */
object JointFlags {
    const val SHOULDER = "SHOULDER"
    const val ELBOW = "ELBOW"
    const val WRIST = "WRIST"
    const val KNEE = "KNEE"
    const val BACK = "BACK"

    val all: List<String> = listOf(SHOULDER, ELBOW, WRIST, KNEE, BACK)
}
