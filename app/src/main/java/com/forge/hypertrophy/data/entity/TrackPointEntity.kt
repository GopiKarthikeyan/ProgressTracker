package com.forge.hypertrophy.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "track_point",
    foreignKeys = [
        ForeignKey(
            entity = CardioLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardioLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardioLogId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardioLogId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Double?,
    val recordedAt: Instant,
    val sequenceIndex: Int,
)
