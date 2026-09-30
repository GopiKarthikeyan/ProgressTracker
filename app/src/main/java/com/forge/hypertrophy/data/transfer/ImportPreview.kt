package com.forge.hypertrophy.data.transfer

data class ImportPreview(
    val programName: String,
    val dayCount: Int,
    val slotCount: Int,
    val newExerciseNames: List<String>,
    val newSkillNames: List<String>,
    val exercisesToArchive: List<String>,
    val errors: List<ProgramJsonIssue>,
    val warnings: List<ProgramJsonIssue>,
)
