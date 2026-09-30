package com.forge.hypertrophy.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SlotPrescription(
    val exerciseId: Long,
    val category: SlotCategory,
    val sortOrder: Int,
    val supersetGroup: Int? = null,
    val metricType: MetricType,
    val setsMin: Int,
    val setsMax: Int,
    val repsLow: Int? = null,
    val repsHigh: Int? = null,
    val isAmrap: Boolean = false,
    val holdTargetSec: Int? = null,
    val blockDurationSec: Int? = null,
    val restMinSec: Int? = null,
    val restMaxSec: Int? = null,
    val restAsNeeded: Boolean = false,
    val isOptional: Boolean = false,
    val skipReasonLabel: String? = null,
    val targetSkillStepId: Long? = null,
    val progressionRule: ProgressionRule,
    val incrementOverrideKg: Double? = null,
    val holdTargetMaxSec: Int? = null,
    val notes: String? = null,
)
