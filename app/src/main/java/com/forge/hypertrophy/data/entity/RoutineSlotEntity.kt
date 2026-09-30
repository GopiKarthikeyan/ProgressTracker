package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory

@Entity(
    tableName = "routine_slot",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SkillStepEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetSkillStepId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("dayId"),
        Index("exerciseId"),
        Index("targetSkillStepId"),
    ],
)
data class RoutineSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val category: SlotCategory,
    val sortOrder: Int,
    val supersetGroup: Int?,
    val metricType: MetricType,
    val setsMin: Int,
    val setsMax: Int,
    val repsLow: Int?,
    val repsHigh: Int?,
    val isAmrap: Boolean,
    val holdTargetSec: Int?,
    val blockDurationSec: Int?,
    val restMinSec: Int?,
    val restMaxSec: Int?,
    val restAsNeeded: Boolean,
    val isOptional: Boolean,
    val skipReasonLabel: String?,
    val targetSkillStepId: Long?,
    val progressionRule: ProgressionRule,
    val incrementOverrideKg: Double?,
    val notes: String? = null,
    val holdTargetMaxSec: Int? = null,
)
