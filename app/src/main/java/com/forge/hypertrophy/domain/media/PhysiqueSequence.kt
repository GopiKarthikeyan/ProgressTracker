package com.forge.hypertrophy.domain.media

import com.forge.hypertrophy.domain.model.Pose

/** Guided photo order. */
val POSE_ORDER: List<Pose> = listOf(
    Pose.FRONT,
    Pose.SIDE,
    Pose.BACK,
    Pose.QUADRICEPS,
    Pose.HAMSTRINGS,
    Pose.CALVES,
)

/** The next pose still to shoot, or null when the sequence is complete. */
fun nextPose(done: Set<Pose>): Pose? = POSE_ORDER.firstOrNull { it !in done }
