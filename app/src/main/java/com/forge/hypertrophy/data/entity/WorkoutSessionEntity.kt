package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("dayId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val dayId: Long?,
    val kind: SessionKind,
    val status: SessionStatus,
    val isDeload: Boolean,
    val isShortOnTime: Boolean,
    val readinessSleep: Int?,
    val readinessSoreness: Int?,
    val readinessEnergy: Int?,
    val startedAt: Instant?,
    val completedAt: Instant?,
)
