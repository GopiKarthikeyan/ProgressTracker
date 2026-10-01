package com.forge.hypertrophy.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ProgramListRoute

@Serializable
data class ProgramEditorRoute(val programId: Long)

@Serializable
data class DayEditorRoute(val dayId: Long)

@Serializable
data class SlotEditorRoute(val slotId: Long)

@Serializable
data object ExerciseLibraryRoute

@Serializable
data class ExerciseEditorRoute(val exerciseId: Long = 0L)

@Serializable
data object SkillLibraryRoute

@Serializable
data class SkillEditorRoute(val skillId: Long = 0L)

/** Preview of a document before import. [uri] is a picked document; [sample] loads the bundled sample. */
@Serializable
data class ImportPreviewRoute(val uri: String? = null, val sample: Boolean = false)
