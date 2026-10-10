package com.forge.hypertrophy.domain.cardio

import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioStyle

enum class CardioDistanceUnit {
    KM,
    M,
}

data class CardioFieldSpec(
    val activity: CardioActivity,
    val showsDistance: Boolean,
    val distanceRequired: Boolean,
    val distanceUnit: CardioDistanceUnit,
    val showsStyle: Boolean,
    val showsGear: Boolean,
    val gearIsShoe: Boolean,
    val showsElevation: Boolean,
    val showsCount: Boolean,
    val countIsLaps: Boolean,
    val countIsJumps: Boolean,
    val countIsFloors: Boolean,
    val showsCustomName: Boolean,
    val allowsGps: Boolean,
)

fun cardioFieldSpec(activity: CardioActivity): CardioFieldSpec = when (activity) {
    CardioActivity.RUNNING -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = true,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = true,
        showsGear = true,
        gearIsShoe = true,
        showsElevation = false,
        showsCount = false,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = true,
    )
    CardioActivity.CYCLING -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = true,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = false,
        showsGear = true,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = false,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = true,
    )
    CardioActivity.HIKING -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = true,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = false,
        showsGear = true,
        gearIsShoe = true,
        showsElevation = true,
        showsCount = false,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = true,
    )
    CardioActivity.SWIMMING -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = true,
        distanceUnit = CardioDistanceUnit.M,
        showsStyle = false,
        showsGear = false,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = true,
        countIsLaps = true,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = false,
    )
    CardioActivity.ROWING, CardioActivity.SKI_ERG -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = true,
        distanceUnit = CardioDistanceUnit.M,
        showsStyle = false,
        showsGear = false,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = false,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = false,
    )
    CardioActivity.ELLIPTICAL -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = false,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = false,
        showsGear = false,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = false,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = false,
    )
    CardioActivity.JUMP_ROPE -> CardioFieldSpec(
        activity = activity,
        showsDistance = false,
        distanceRequired = false,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = false,
        showsGear = false,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = true,
        countIsLaps = false,
        countIsJumps = true,
        countIsFloors = false,
        showsCustomName = false,
        allowsGps = false,
    )
    CardioActivity.STAIR_CLIMBER -> CardioFieldSpec(
        activity = activity,
        showsDistance = false,
        distanceRequired = false,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = false,
        showsGear = false,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = true,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = true,
        showsCustomName = false,
        allowsGps = false,
    )
    CardioActivity.CUSTOM -> CardioFieldSpec(
        activity = activity,
        showsDistance = true,
        distanceRequired = false,
        distanceUnit = CardioDistanceUnit.KM,
        showsStyle = false,
        showsGear = false,
        gearIsShoe = false,
        showsElevation = false,
        showsCount = false,
        countIsLaps = false,
        countIsJumps = false,
        countIsFloors = false,
        showsCustomName = true,
        allowsGps = false,
    )
}

data class CardioEntryInput(
    val activity: CardioActivity,
    val style: CardioStyle,
    val customName: String,
    val distanceM: Double?,
    val durationSec: Int,
    val elevationM: Double?,
    val count: Int?,
)

enum class CardioEntryError {
    DURATION,
    DISTANCE,
    CUSTOM_NAME,
    STYLE,
}

fun validateCardioEntry(input: CardioEntryInput): CardioEntryError? {
    val spec = cardioFieldSpec(input.activity)
    if (input.durationSec <= 0) return CardioEntryError.DURATION
    if (spec.showsCustomName && input.customName.isBlank()) return CardioEntryError.CUSTOM_NAME
    if (spec.showsStyle) {
        if (input.style == CardioStyle.NONE) return CardioEntryError.STYLE
    } else if (input.style != CardioStyle.NONE) {
        return CardioEntryError.STYLE
    }
    val distance = input.distanceM ?: 0.0
    if (spec.distanceRequired && distance <= 0.0) return CardioEntryError.DISTANCE
    if (!spec.showsDistance && distance > 0.0) return CardioEntryError.DISTANCE
    return null
}

fun defaultStyleFor(activity: CardioActivity): CardioStyle =
    if (activity == CardioActivity.RUNNING) CardioStyle.JOG else CardioStyle.NONE
