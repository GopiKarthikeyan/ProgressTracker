package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.Equipment
import java.time.Instant

@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("skillId")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val equipment: Equipment,
    val barWeightKg: Double?,
    val loadIncrementKg: Double,
    val isUnilateral: Boolean,
    val skillId: Long?,
    val primaryMuscleGroups: List<String>,
    val secondaryMuscleGroups: List<String>,
    val setupNotes: String,
    val archivedAt: Instant?,
)
