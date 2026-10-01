package com.forge.hypertrophy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioType

@Entity(
    tableName = "cardio_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GearEntity::class,
            parentColumns = ["id"],
            childColumns = ["gearId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["sessionId"], unique = true),
        Index("gearId"),
    ],
)
data class CardioLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val distanceM: Double,
    val durationSec: Int,
    val source: CardioSource,
    val gearId: Long?,
    val tempC: Double?,
    val uvIndex: Double?,
    @ColumnInfo(defaultValue = "'JOG'") val type: CardioType = CardioType.JOG,
)
