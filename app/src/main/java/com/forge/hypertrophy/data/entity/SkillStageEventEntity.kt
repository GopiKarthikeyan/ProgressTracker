package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One tier or stage change on a skill ladder. [SkillProgressEntity] only
 * keeps the current position; this log is what the weekly review reads.
 */
@Entity(
    tableName = "skill_stage_event",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("skillId"), Index("date")],
)
data class SkillStageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillId: Long,
    val date: LocalDate,
    val fromTier: Int,
    val fromStage: Int,
    val toTier: Int,
    val toStage: Int,
    val recordedAt: Instant,
)
