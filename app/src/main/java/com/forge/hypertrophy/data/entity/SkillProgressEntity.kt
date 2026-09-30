package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "skill_progress",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SkillStepEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentStepId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["skillId"], unique = true),
        Index("currentStepId"),
    ],
)
data class SkillProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillId: Long,
    val currentStepId: Long,
    val stage: Int,
    val updatedAt: Instant,
)
