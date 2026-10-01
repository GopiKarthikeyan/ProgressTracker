package com.forge.hypertrophy.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object GalleryRoute

/** Records a clip for [exerciseId]; [setEntryId] attaches it to a logged set. */
@Serializable
data class VideoCaptureRoute(val exerciseId: Long, val setEntryId: Long? = null)

@Serializable
data object PhysiqueCaptureRoute

@Serializable
data class VideoComparisonRoute(val leftId: Long, val rightId: Long)
