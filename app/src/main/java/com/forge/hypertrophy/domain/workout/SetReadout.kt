package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType

/**
 * What the working-set card offers steppers for. A hold never shows a load,
 * and a rep slot never shows a bar. Added load appears only for a weighted
 * prescription.
 */
enum class SetReadout {
    HOLD,
    REPS,
    WEIGHT_AND_REPS,
    HOLD_AND_REPS,
    WEIGHT_HOLD_AND_REPS,
}

fun setReadout(metric: MetricType, equipment: Equipment): SetReadout = when (metric) {
    MetricType.HOLD, MetricType.TIMED_BLOCK -> SetReadout.HOLD
    MetricType.REPS -> SetReadout.REPS
    MetricType.WEIGHT_REPS -> SetReadout.WEIGHT_AND_REPS
    MetricType.HOLD_OR_REPS -> if (equipment == Equipment.WEIGHTED_BODYWEIGHT) {
        SetReadout.WEIGHT_HOLD_AND_REPS
    } else {
        SetReadout.HOLD_AND_REPS
    }
}

fun SetReadout.showsWeight(): Boolean =
    this == SetReadout.WEIGHT_AND_REPS || this == SetReadout.WEIGHT_HOLD_AND_REPS

fun SetReadout.showsReps(): Boolean = this != SetReadout.HOLD

fun SetReadout.showsHold(): Boolean =
    this == SetReadout.HOLD || this == SetReadout.HOLD_AND_REPS || this == SetReadout.WEIGHT_HOLD_AND_REPS
